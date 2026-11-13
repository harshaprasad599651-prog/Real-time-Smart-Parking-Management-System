package com.smartpark.server.service;

import com.smartpark.server.document.ParkingSession;
import com.smartpark.server.document.ParkingSlot;
import com.smartpark.server.document.SessionStatus;
import com.smartpark.server.document.SlotStatus;
import com.smartpark.server.dto.ParkingSessionDto;
import com.smartpark.server.dto.VehicleEntryRequest;
import com.smartpark.server.dto.VehicleExitRequest;
import com.smartpark.server.exception.OperatorNotAuthorizedException;
import com.smartpark.server.exception.SmartParkException;
import com.smartpark.server.repository.ParkingSessionRepository;
import com.smartpark.server.repository.ParkingSlotRepository;
import com.smartpark.server.support.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Database consistency of the critical parking operations under concurrent, mixed load.
 * Whatever the interleaving, afterwards the slots and the sessions must agree exactly.
 */
class ParkingConsistencyTest extends AbstractMongoIntegrationTest {

    private static final List<String> SLOTS = List.of("A-01", "A-02", "A-03", "A-04", "A-05", "A-06");

    @Autowired private ParkingSessionService sessionService;
    @Autowired private ParkingSlotService slotService;
    @Autowired private UserService userService;
    @Autowired private ParkingSlotRepository slotRepository;
    @Autowired private ParkingSessionRepository sessionRepository;

    @BeforeEach
    void seed() {
        seedSampleData();
    }

    /**
     * 12 terminals perform 30 random entries and exits each, all at once, over 6 slots and
     * 15 vehicles, so most requests collide. Rejections are expected; inconsistencies are not.
     */
    @RepeatedTest(3)
    void mixedConcurrentEntriesAndExitsLeaveSlotsAndSessionsConsistent() throws Exception {
        int terminals = 12;
        int opsEach = 30;
        AtomicInteger entries = new AtomicInteger();
        AtomicInteger exits = new AtomicInteger();
        List<Throwable> unexpected = new java.util.concurrent.CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(terminals);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < terminals; t++) {
            futures.add(pool.submit(() -> {
                go.await();
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                for (int i = 0; i < opsEach; i++) {
                    String vehicle = "CAR" + rnd.nextInt(15);
                    try {
                        if (rnd.nextBoolean()) {
                            sessionService.startParking(new VehicleEntryRequest(vehicle, "CAR",
                                    SLOTS.get(rnd.nextInt(SLOTS.size())), "entry1"));
                            entries.incrementAndGet();
                        } else {
                            sessionService.completeParking(new VehicleExitRequest(vehicle, "exit1"));
                            exits.incrementAndGet();
                        }
                    } catch (SmartParkException expected) {
                        // slot taken, vehicle already parked, not parked ... : the normal outcome of a collision
                    } catch (RuntimeException e) {
                        unexpected.add(e);
                    }
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(unexpected).as("unexpected errors").isEmpty();
        assertThat(entries.get()).isPositive();
        assertThat(exits.get()).isPositive();
        assertConsistent();
        assertThat(sessionRepository.count()).isEqualTo(entries.get());
        assertThat(sessionRepository.countByStatus(SessionStatus.COMPLETED)).isEqualTo(exits.get());
    }

    @RepeatedTest(5)
    void disablingASlotWhileItIsBeingAllocatedHasExactlyOneWinner() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Boolean> entry = pool.submit(() -> {
            go.await();
            try {
                sessionService.startParking(new VehicleEntryRequest("RACE1", "CAR", "A-02", "entry1"));
                return true;
            } catch (SmartParkException e) {
                return false;
            }
        });
        Future<Boolean> disable = pool.submit(() -> {
            go.await();
            try {
                slotService.disableSlot("A-02", "admin");
                return true;
            } catch (SmartParkException e) {
                return false;
            }
        });
        go.countDown();
        boolean entered = entry.get(30, TimeUnit.SECONDS);
        boolean disabled = disable.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(entered ^ disabled).as("exactly one of entry / disable succeeds").isTrue();
        SlotStatus status = slotRepository.findBySlotCode("A-02").orElseThrow().getStatus();
        assertThat(status).isEqualTo(entered ? SlotStatus.OCCUPIED : SlotStatus.DISABLED);
        assertThat(sessionRepository.countByStatus(SessionStatus.ACTIVE)).isEqualTo(entered ? 1 : 0);
        assertConsistent();
    }

    @Test
    void aVehicleCanReturnAfterLeavingWithANewTicket() {
        ParkingSessionDto first = sessionService.startParking(new VehicleEntryRequest("BACK1", "CAR", "A-01", "entry1"));
        sessionService.completeParking(new VehicleExitRequest("BACK1", "exit1"));
        ParkingSessionDto second = sessionService.startParking(new VehicleEntryRequest("BACK1", "CAR", "A-01", "entry1"));

        assertThat(second.ticketId()).isNotEqualTo(first.ticketId());
        assertThat(sessionRepository.findByVehicleNumberOrderByEntryTimeDesc("BACK1"))
                .extracting(ParkingSession::getStatus)
                .containsExactly(SessionStatus.ACTIVE, SessionStatus.COMPLETED);
        assertConsistent();
    }

    @Test
    void aDeactivatedOperatorCannotRegisterEntriesEvenWithAnOldRequest() {
        userService.setActive("entry1", false, "admin");
        assertThatThrownBy(() -> sessionService.startParking(new VehicleEntryRequest("LATE1", "CAR", "A-01", "entry1")))
                .isInstanceOf(OperatorNotAuthorizedException.class);
        assertThat(sessionRepository.count()).isZero();
        assertThat(slotRepository.findBySlotCode("A-01").orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void anExitOperatorCannotRegisterEntriesAndAnEntryOperatorCannotRegisterExits() {
        assertThatThrownBy(() -> sessionService.startParking(new VehicleEntryRequest("ROLE1", "CAR", "A-01", "exit1")))
                .isInstanceOf(OperatorNotAuthorizedException.class);
        sessionService.startParking(new VehicleEntryRequest("ROLE1", "CAR", "A-01", "entry1"));
        assertThatThrownBy(() -> sessionService.completeParking(new VehicleExitRequest("ROLE1", "entry1")))
                .isInstanceOf(OperatorNotAuthorizedException.class);
        assertThat(sessionRepository.countByStatus(SessionStatus.ACTIVE)).isEqualTo(1);
    }

    /** Slots and sessions agree: one ACTIVE session per OCCUPIED slot, with the same ticket. */
    private void assertConsistent() {
        List<ParkingSession> active = sessionRepository.findByStatusOrderByEntryTimeDesc(SessionStatus.ACTIVE);
        List<ParkingSlot> slots = slotRepository.findAll();
        Map<String, ParkingSlot> bySlot = slots.stream().collect(Collectors.toMap(ParkingSlot::getSlotCode, Function.identity()));

        Set<String> vehicles = new HashSet<>();
        Set<String> slotCodes = new HashSet<>();
        for (ParkingSession s : active) {
            assertThat(vehicles.add(s.getVehicleNumber())).as("vehicle %s parked twice", s.getVehicleNumber()).isTrue();
            assertThat(slotCodes.add(s.getParkingSlotCode())).as("slot %s used twice", s.getParkingSlotCode()).isTrue();
            ParkingSlot slot = bySlot.get(s.getParkingSlotCode());
            assertThat(slot.getStatus()).as("slot of active session %s", s.getTicketId()).isEqualTo(SlotStatus.OCCUPIED);
            assertThat(slot.getCurrentTicketId()).isEqualTo(s.getTicketId());
        }
        Map<String, Boolean> seen = new ConcurrentHashMap<>();
        for (ParkingSlot slot : slots) {
            if (slot.getStatus() == SlotStatus.OCCUPIED) {
                assertThat(slotCodes).as("occupied slot %s has an active session", slot.getSlotCode()).contains(slot.getSlotCode());
            } else {
                assertThat(slot.getCurrentTicketId()).as("free slot %s holds no ticket", slot.getSlotCode()).isNull();
            }
            seen.put(slot.getSlotCode(), true);
        }
        assertThat(slots.stream().filter(s -> s.getStatus() == SlotStatus.OCCUPIED).count()).isEqualTo(active.size());
    }
}
