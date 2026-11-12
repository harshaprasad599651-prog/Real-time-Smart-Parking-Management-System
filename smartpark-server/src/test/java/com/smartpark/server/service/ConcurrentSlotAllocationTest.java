package com.smartpark.server.service;

import com.smartpark.server.document.ParkingSession;
import com.smartpark.server.document.ParkingSlot;
import com.smartpark.server.document.SessionStatus;
import com.smartpark.server.document.SlotStatus;
import com.smartpark.server.dto.ParkingSessionDto;
import com.smartpark.server.dto.VehicleEntryRequest;
import com.smartpark.server.dto.VehicleExitRequest;
import com.smartpark.server.exception.NoActiveSessionException;
import com.smartpark.server.exception.SmartParkException;
import com.smartpark.server.persistence.MongoTransactionRunner;
import com.smartpark.server.repository.ParkingSessionRepository;
import com.smartpark.server.repository.ParkingSlotRepository;
import com.smartpark.server.support.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Many operators act at exactly the same moment. All threads wait on a latch and are
 * released together to maximise contention. Whatever the interleaving:
 * one slot holds at most one vehicle, and one vehicle holds at most one slot.
 */
class ConcurrentSlotAllocationTest extends AbstractMongoIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentSlotAllocationTest.class);
    private static final String[] OPERATORS = {"entry1", "admin"}; // both may register entries

    @Autowired private ParkingSessionService sessionService;
    @Autowired private ParkingSlotRepository slotRepository;
    @Autowired private ParkingSessionRepository sessionRepository;
    @Autowired private MongoTransactionRunner transactionRunner;

    @BeforeEach
    void seed() {
        seedSampleData();
        log.info("Running concurrency test with transactions {}",
                transactionRunner.transactionsSupported() ? "ENABLED" : "DISABLED (atomic operations + compensation)");
    }

    @RepeatedTest(3)
    void onlyOneOfTwentyOperatorsGetsTheSameSlot() throws Exception {
        int threads = 20;
        List<Callable<ParkingSessionDto>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String vehicle = "CAR%03d".formatted(i);
            String operator = OPERATORS[i % OPERATORS.length];
            tasks.add(() -> sessionService.startParking(new VehicleEntryRequest(vehicle, "CAR", "A-01", operator)));
        }

        Outcome<ParkingSessionDto> outcome = runSimultaneously(tasks);

        assertThat(outcome.successes()).hasSize(1);
        assertThat(outcome.failures()).hasSize(threads - 1).allMatch(e -> e instanceof SmartParkException);

        ParkingSessionDto winner = outcome.successes().getFirst();
        ParkingSlot slot = slotRepository.findBySlotCode("A-01").orElseThrow();
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.OCCUPIED);
        assertThat(slot.getCurrentTicketId()).isEqualTo(winner.ticketId());
        assertThat(sessionRepository.findByParkingSlotCodeAndStatus("A-01", SessionStatus.ACTIVE))
                .get().extracting(ParkingSession::getVehicleNumber).isEqualTo(winner.vehicleNumber());
        assertThat(sessionRepository.countByStatus(SessionStatus.ACTIVE)).isEqualTo(1);
    }

    @RepeatedTest(3)
    void sameVehicleRacingForManySlotsGetsExactlyOne() throws Exception {
        List<String> slotCodes = List.of("A-01", "A-02", "A-03", "A-04", "A-05", "A-06", "A-07", "A-08", "C-01", "C-02");
        List<Callable<ParkingSessionDto>> tasks = slotCodes.stream()
                .<Callable<ParkingSessionDto>>map(code ->
                        () -> sessionService.startParking(new VehicleEntryRequest("WP CAB-1234", "CAR", code, "entry1")))
                .toList();

        Outcome<ParkingSessionDto> outcome = runSimultaneously(tasks);

        assertThat(outcome.successes()).hasSize(1);
        assertThat(outcome.failures()).hasSize(slotCodes.size() - 1).allMatch(e -> e instanceof SmartParkException);

        // The losers' slot claims were rolled back / compensated: only the winner's slot is occupied.
        String winningSlot = outcome.successes().getFirst().parkingSlotCode();
        Map<String, SlotStatus> statuses = slotCodes.stream().collect(Collectors.toMap(Function.identity(),
                c -> slotRepository.findBySlotCode(c).orElseThrow().getStatus()));
        assertThat(statuses).allSatisfy((code, status) ->
                assertThat(status).as(code).isEqualTo(code.equals(winningSlot) ? SlotStatus.OCCUPIED : SlotStatus.AVAILABLE));
        assertThat(sessionRepository.countByStatus(SessionStatus.ACTIVE)).isEqualTo(1);
    }

    @Test
    void manyVehiclesCompetingForFewSlotsNeverDoubleBook() throws Exception {
        List<String> slotCodes = List.of("A-01", "A-02", "A-03", "A-04", "A-05");
        int vehicles = 40;
        List<Callable<ParkingSessionDto>> tasks = new ArrayList<>();
        for (int i = 0; i < vehicles; i++) {
            String vehicle = "VEH%03d".formatted(i);
            String slot = slotCodes.get(i % slotCodes.size());
            tasks.add(() -> sessionService.startParking(new VehicleEntryRequest(vehicle, "CAR", slot, "entry1")));
        }

        Outcome<ParkingSessionDto> outcome = runSimultaneously(tasks);

        // Each slot was won exactly once.
        assertThat(outcome.successes()).hasSize(slotCodes.size());
        assertThat(outcome.successes()).extracting(ParkingSessionDto::parkingSlotCode)
                .containsExactlyInAnyOrderElementsOf(slotCodes);
        assertThat(slotRepository.countByStatus(SlotStatus.OCCUPIED)).isEqualTo(slotCodes.size());
        assertThat(sessionRepository.countByStatus(SessionStatus.ACTIVE)).isEqualTo(slotCodes.size());
        for (ParkingSessionDto s : outcome.successes()) {
            assertThat(slotRepository.findBySlotCode(s.parkingSlotCode()).orElseThrow().getCurrentTicketId())
                    .isEqualTo(s.ticketId());
        }
    }

    @Test
    void onlyOneOfConcurrentExitsSucceeds() throws Exception {
        sessionService.startParking(new VehicleEntryRequest("ABC123", "CAR", "A-01", "entry1"));
        List<Callable<ParkingSessionDto>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String operator = i % 2 == 0 ? "exit1" : "admin";
            tasks.add(() -> sessionService.completeParking(new VehicleExitRequest("ABC123", operator)));
        }

        Outcome<ParkingSessionDto> outcome = runSimultaneously(tasks);

        assertThat(outcome.successes()).hasSize(1);
        assertThat(outcome.failures()).hasSize(9).allMatch(e -> e instanceof NoActiveSessionException
                || e instanceof SmartParkException);
        assertThat(slotRepository.findBySlotCode("A-01").orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(sessionRepository.countByStatus(SessionStatus.COMPLETED)).isEqualTo(1);
    }

    // ----------------------------------------------------------------- helpers

    private record Outcome<T>(List<T> successes, List<Throwable> failures) {
    }

    private static <T> Outcome<T> runSimultaneously(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown(); // release all threads at once

            List<T> successes = new ArrayList<>();
            List<Throwable> failures = new ArrayList<>();
            for (Future<T> f : futures) {
                try {
                    successes.add(f.get(60, TimeUnit.SECONDS));
                } catch (java.util.concurrent.ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            failures.stream().filter(e -> !(e instanceof SmartParkException))
                    .forEach(e -> log.error("Unexpected failure type", e));
            return new Outcome<>(successes, failures);
        } finally {
            pool.shutdownNow();
        }
    }
}
