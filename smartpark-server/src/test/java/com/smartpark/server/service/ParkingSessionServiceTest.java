package com.smartpark.server.service;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.ParkingSlot;
import com.smartpark.server.document.SessionStatus;
import com.smartpark.server.document.SlotStatus;
import com.smartpark.server.dto.ActivityLogDto;
import com.smartpark.server.dto.ParkingSessionDto;
import com.smartpark.server.dto.ParkingSlotDto;
import com.smartpark.server.dto.VehicleEntryRequest;
import com.smartpark.server.dto.VehicleExitRequest;
import com.smartpark.server.exception.InvalidStateException;
import com.smartpark.server.exception.NoActiveSessionException;
import com.smartpark.server.exception.OperatorNotAuthorizedException;
import com.smartpark.server.exception.ResourceNotFoundException;
import com.smartpark.server.exception.SlotNotCompatibleException;
import com.smartpark.server.exception.SlotUnavailableException;
import com.smartpark.server.support.AbstractMongoIntegrationTest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

class ParkingSessionServiceTest extends AbstractMongoIntegrationTest {

    @Autowired private ParkingSessionService sessionService;
    @Autowired private ParkingSlotService slotService;
    @Autowired private UserService userService;
    @Autowired private ActivityLogService activityLogService;

    @BeforeEach
    void seed() {
        seedSampleData();
    }

    @Test
    void entryThenExitLifecycle() {
        ParkingSessionDto entry = sessionService.startParking(
                new VehicleEntryRequest("wp cab-1234", "car", "a-01", "entry1"));

        assertThat(entry.ticketId()).matches("SP-\\d{8}-\\d{6}");
        assertThat(entry.vehicleNumber()).isEqualTo("WPCAB1234");
        assertThat(entry.vehicleCategory()).isEqualTo("CAR");
        assertThat(entry.parkingSlotCode()).isEqualTo("A-01");
        assertThat(entry.status()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(entry.entryTime()).isNotNull();
        assertThat(entry.entryOperator()).isEqualTo("entry1");
        assertThat(slotService.getSlot("A-01").status()).isEqualTo(SlotStatus.OCCUPIED);
        assertThat(slotService.getSlot("A-01").currentTicketId()).isEqualTo(entry.ticketId());
        assertThat(sessionService.findActiveSession("WPCAB1234")).contains(entry);

        ParkingSessionDto exit = sessionService.completeParking(new VehicleExitRequest("WP-CAB 1234", "exit1"));

        assertThat(exit.ticketId()).isEqualTo(entry.ticketId());
        assertThat(exit.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(exit.exitTime()).isAfterOrEqualTo(entry.entryTime());
        assertThat(exit.exitOperator()).isEqualTo("exit1");
        assertThat(slotService.getSlot("A-01").status()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(slotService.getSlot("A-01").currentTicketId()).isNull();
        assertThat(sessionService.findActiveSession("WPCAB1234")).isEmpty();
        assertThat(activityLogService.recent(50)).extracting(ActivityLogDto::action)
                .contains(ActivityAction.VEHICLE_ENTRY, ActivityAction.VEHICLE_EXIT);
    }

    @Test
    void ticketNumbersIncrease() {
        String t1 = sessionService.startParking(new VehicleEntryRequest("AAA111", "CAR", "A-01", "entry1")).ticketId();
        String t2 = sessionService.startParking(new VehicleEntryRequest("BBB222", "CAR", "A-02", "entry1")).ticketId();

        assertThat(t2).isGreaterThan(t1);
    }

    @Test
    void availableSlotsAreFilteredByCategory() {
        assertThat(slotService.listAvailableSlots("MOTORCYCLE")).hasSize(6)
                .extracting(ParkingSlotDto::slotCode).allMatch(code -> code.startsWith("M-"));
        assertThat(slotService.listAvailableSlots("VAN")).extracting(ParkingSlotDto::slotCode)
                .containsExactly("B-01", "B-02", "B-03", "B-04");

        sessionService.startParking(new VehicleEntryRequest("VAN001", "VAN", "B-01", "entry1"));

        assertThat(slotService.listAvailableSlots("VAN")).hasSize(3);
        assertThat(slotService.occupancySummary().occupied()).isEqualTo(1);
    }

    @Test
    void rejectsIncompatibleSlot() {
        assertThatThrownBy(() -> sessionService.startParking(new VehicleEntryRequest("MC1234", "MOTORCYCLE", "A-01", "entry1")))
                .isInstanceOf(SlotNotCompatibleException.class);
        assertThat(slotService.getSlot("A-01").status()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void rejectsUnknownSlotAndCategory() {
        assertThatThrownBy(() -> sessionService.startParking(new VehicleEntryRequest("AAA111", "CAR", "Z-99", "entry1")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> sessionService.startParking(new VehicleEntryRequest("AAA111", "TRUCK", "A-01", "entry1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void disabledSlotsCannotBeAllocatedAndOccupiedSlotsCannotBeDisabled() {
        slotService.disableSlot("A-01", "admin");
        assertThatThrownBy(() -> sessionService.startParking(new VehicleEntryRequest("AAA111", "CAR", "A-01", "entry1")))
                .isInstanceOf(SlotUnavailableException.class).hasMessageContaining("disabled");

        sessionService.startParking(new VehicleEntryRequest("AAA111", "CAR", "A-02", "entry1"));
        assertThatThrownBy(() -> slotService.disableSlot("A-02", "admin")).isInstanceOf(InvalidStateException.class);

        slotService.enableSlot("A-01", "admin");
        assertThat(slotService.getSlot("A-01").status()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void enforcesOperatorRoles() {
        assertThatThrownBy(() -> sessionService.startParking(new VehicleEntryRequest("AAA111", "CAR", "A-01", "exit1")))
                .isInstanceOf(OperatorNotAuthorizedException.class);
        assertThatThrownBy(() -> sessionService.startParking(new VehicleEntryRequest("AAA111", "CAR", "A-01", "nobody")))
                .isInstanceOf(OperatorNotAuthorizedException.class);

        sessionService.startParking(new VehicleEntryRequest("AAA111", "CAR", "A-01", "entry1"));
        assertThatThrownBy(() -> sessionService.completeParking(new VehicleExitRequest("AAA111", "entry1")))
                .isInstanceOf(OperatorNotAuthorizedException.class);

        userService.setActive("exit1", false, "admin");
        assertThatThrownBy(() -> sessionService.completeParking(new VehicleExitRequest("AAA111", "exit1")))
                .isInstanceOf(OperatorNotAuthorizedException.class).hasMessageContaining("inactive");
    }

    @Test
    void exitWithoutActiveSessionFails() {
        assertThatThrownBy(() -> sessionService.completeParking(new VehicleExitRequest("NOPE123", "exit1")))
                .isInstanceOf(NoActiveSessionException.class);
    }

    @Test
    void validatesInput() {
        assertThatThrownBy(() -> sessionService.startParking(new VehicleEntryRequest("!!", "CAR", "A-01", "entry1")))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("vehicleNumber");
        assertThatThrownBy(() -> sessionService.startParking(new VehicleEntryRequest("AAA111", "", "A-01", "entry1")))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("categoryName");
        assertThatThrownBy(() -> sessionService.startParking(null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void authenticatesSeededUsersWithHashedPasswords() {
        assertThat(userService.authenticate("ADMIN", "Admin@123")).isPresent();
        assertThat(userService.authenticate("admin", "wrong-password")).isEmpty();
        String storedHash = mongoTemplate.getCollection("users").find(new org.bson.Document("username", "admin"))
                .first().getString("passwordHash");
        assertThat(storedHash).startsWith("$2").doesNotContain("Admin@123");
    }

    @Test
    void reconciliationReleasesOnlyOldOrphanedSlots() {
        Instant longAgo = Instant.now().minus(Duration.ofMinutes(10));
        // A-03: orphan (OCCUPIED, no session) older than the grace period -> released
        markOccupied("A-03", "SP-ORPHAN-1", longAgo);
        // A-04: orphan, but too recent (an allocation could still be in progress) -> kept
        markOccupied("A-04", "SP-ORPHAN-2", Instant.now());
        // A-05: legitimately occupied -> kept
        ParkingSessionDto real = sessionService.startParking(new VehicleEntryRequest("AAA111", "CAR", "A-05", "entry1"));
        mongoTemplate.updateFirst(query(where("slotCode").is("A-05")),
                new Update().set("statusChangedAt", longAgo), ParkingSlot.class);

        int released = sessionService.reconcileOrphanedSlots(Duration.ofMinutes(2));

        assertThat(released).isEqualTo(1);
        assertThat(slotService.getSlot("A-03").status()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(slotService.getSlot("A-04").status()).isEqualTo(SlotStatus.OCCUPIED);
        assertThat(slotService.getSlot("A-05").status()).isEqualTo(SlotStatus.OCCUPIED);
        assertThat(slotService.getSlot("A-05").currentTicketId()).isEqualTo(real.ticketId());
    }

    private void markOccupied(String slotCode, String ticketId, Instant when) {
        mongoTemplate.updateFirst(query(where("slotCode").is(slotCode)),
                new Update().set("status", SlotStatus.OCCUPIED).set("currentTicketId", ticketId)
                        .set("statusChangedAt", when).inc("version", 1),
                ParkingSlot.class);
    }
}
