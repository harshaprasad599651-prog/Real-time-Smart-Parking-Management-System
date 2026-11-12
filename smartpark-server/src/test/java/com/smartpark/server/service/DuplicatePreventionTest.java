package com.smartpark.server.service;

import com.smartpark.server.document.ParkingSession;
import com.smartpark.server.document.SessionStatus;
import com.smartpark.server.document.SlotStatus;
import com.smartpark.server.document.SlotType;
import com.smartpark.server.document.User;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.dto.CreateParkingSlotRequest;
import com.smartpark.server.dto.CreateUserRequest;
import com.smartpark.server.dto.CreateVehicleCategoryRequest;
import com.smartpark.server.dto.ParkingSessionDto;
import com.smartpark.server.dto.VehicleEntryRequest;
import com.smartpark.server.dto.VehicleExitRequest;
import com.smartpark.server.exception.DuplicateResourceException;
import com.smartpark.server.exception.SlotUnavailableException;
import com.smartpark.server.exception.VehicleAlreadyParkedException;
import com.smartpark.server.repository.ParkingSessionRepository;
import com.smartpark.server.repository.UserRepository;
import com.smartpark.server.support.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Duplicate prevention, tested at two levels: the service (friendly exceptions) and the
 * raw database (unique indexes reject writes that bypass the service).
 */
class DuplicatePreventionTest extends AbstractMongoIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private VehicleCategoryService categoryService;
    @Autowired private ParkingSlotService slotService;
    @Autowired private ParkingSessionService sessionService;
    @Autowired private ParkingSessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void seed() {
        seedSampleData();
    }

    // ------------------------------------------------------ users / categories / slots

    @Test
    void rejectsDuplicateUsernameCaseInsensitively() {
        assertThatThrownBy(() -> userService.createUser(
                new CreateUserRequest("Entry1", "password123", UserRole.ENTRY_OPERATOR), "admin"))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void uniqueIndexRejectsDuplicateUsernameEvenWithoutService() {
        assertThatThrownBy(() -> userRepository.insert(
                new User("admin", "x", UserRole.ADMIN, true, Instant.now())))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void rejectsDuplicateCategoryName() {
        assertThatThrownBy(() -> categoryService.createCategory(
                new CreateVehicleCategoryRequest("car", "again", EnumSet.of(SlotType.STANDARD)), "admin"))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsDuplicateSlotCode() {
        assertThatThrownBy(() -> slotService.createSlot(
                new CreateParkingSlotRequest("a-01", SlotType.STANDARD, null, 5, "Z", 1), "admin"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("A-01");
    }

    @Test
    void rejectsTwoSlotsAtTheSameMapPosition() {
        // A-01 is at floor 0, row A, column 1
        assertThatThrownBy(() -> slotService.createSlot(
                new CreateParkingSlotRequest("X-99", SlotType.STANDARD, null, 0, "A", 1), "admin"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("floor 0");
    }

    // ------------------------------------------------------------- active vehicles

    @Test
    void rejectsSecondActiveSessionForSameVehicleAcrossFormatting() {
        sessionService.startParking(entry("wp cab-1234", "CAR", "A-01"));

        assertThatThrownBy(() -> sessionService.startParking(entry("WPCAB1234", "CAR", "A-02")))
                .isInstanceOf(VehicleAlreadyParkedException.class);
        assertThat(slotService.getSlot("A-02").status()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void uniqueIndexRejectsSecondActiveSessionForSameVehicle() {
        mongoTemplate.insert(ParkingSession.start("T-1", "ABC123", "CAR", "A-01", Instant.now(), "entry1"));

        assertThatThrownBy(() -> mongoTemplate.insert(
                ParkingSession.start("T-2", "ABC123", "CAR", "A-02", Instant.now(), "entry1")))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("ux_sessions_active_vehicle");
    }

    // ---------------------------------------------------------------- active slots

    @Test
    void rejectsSecondVehicleInOccupiedSlot() {
        sessionService.startParking(entry("AAA111", "CAR", "A-01"));

        assertThatThrownBy(() -> sessionService.startParking(entry("BBB222", "CAR", "A-01")))
                .isInstanceOf(SlotUnavailableException.class)
                .hasMessageContaining("occupied");
        assertThat(sessionRepository.countByStatus(SessionStatus.ACTIVE)).isEqualTo(1);
    }

    @Test
    void uniqueIndexRejectsSecondActiveSessionForSameSlot() {
        mongoTemplate.insert(ParkingSession.start("T-1", "AAA111", "CAR", "A-01", Instant.now(), "entry1"));

        assertThatThrownBy(() -> mongoTemplate.insert(
                ParkingSession.start("T-2", "BBB222", "CAR", "A-01", Instant.now(), "entry1")))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("ux_sessions_active_slot");
    }

    @Test
    void uniqueIndexRejectsDuplicateTicketId() {
        mongoTemplate.insert(ParkingSession.start("T-1", "AAA111", "CAR", "A-01", Instant.now(), "entry1"));

        assertThatThrownBy(() -> mongoTemplate.insert(
                ParkingSession.start("T-1", "BBB222", "CAR", "A-02", Instant.now(), "entry1")))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("ux_sessions_ticket");
    }

    // --------------------------------------------- history is NOT restricted

    @Test
    void completedSessionsDoNotBlockReuseOfVehicleOrSlot() {
        ParkingSession old1 = ParkingSession.start("T-1", "AAA111", "CAR", "A-01", Instant.now(), "entry1");
        old1.setStatus(SessionStatus.COMPLETED);
        ParkingSession old2 = ParkingSession.start("T-2", "AAA111", "CAR", "A-01", Instant.now(), "entry1");
        old2.setStatus(SessionStatus.COMPLETED);

        assertThatCode(() -> {
            mongoTemplate.insert(old1);
            mongoTemplate.insert(old2);
        }).doesNotThrowAnyException();
    }

    @Test
    void vehicleCanParkAgainAfterExit() {
        ParkingSessionDto first = sessionService.startParking(entry("AAA111", "CAR", "A-01"));
        sessionService.completeParking(new VehicleExitRequest("AAA111", "exit1"));

        ParkingSessionDto second = sessionService.startParking(entry("AAA111", "CAR", "A-01"));

        assertThat(second.ticketId()).isNotEqualTo(first.ticketId());
        assertThat(sessionRepository.findByVehicleNumberOrderByEntryTimeDesc("AAA111")).hasSize(2);
    }

    private static VehicleEntryRequest entry(String vehicle, String category, String slot) {
        return new VehicleEntryRequest(vehicle, category, slot, "entry1");
    }
}
