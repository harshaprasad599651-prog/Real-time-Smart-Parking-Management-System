package com.smartpark.server.persistence;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.ActivityLog;
import com.smartpark.server.document.ParkingSession;
import com.smartpark.server.document.ParkingSlot;
import com.smartpark.server.document.SessionStatus;
import com.smartpark.server.document.SlotStatus;
import com.smartpark.server.document.SlotType;
import com.smartpark.server.document.User;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.document.VehicleCategory;
import com.smartpark.server.repository.ActivityLogRepository;
import com.smartpark.server.repository.ParkingSessionRepository;
import com.smartpark.server.repository.ParkingSlotRepository;
import com.smartpark.server.repository.UserRepository;
import com.smartpark.server.repository.VehicleCategoryRepository;
import com.smartpark.server.service.ActivityLogService;
import com.smartpark.server.support.AbstractMongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Create-and-read round trips for every document type. */
class DocumentPersistenceTest extends AbstractMongoIntegrationTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired private UserRepository users;
    @Autowired private VehicleCategoryRepository categories;
    @Autowired private ParkingSlotRepository slots;
    @Autowired private ParkingSessionRepository sessions;
    @Autowired private ActivityLogRepository activityLogs;
    @Autowired private ActivityLogService activityLogService;

    @Test
    void userRoundTrip() {
        User saved = users.save(new User("alice", "$2a$10$hash", UserRole.ENTRY_OPERATOR, true, NOW));

        User found = users.findByUsername("alice").orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId()).isNotBlank();
        assertThat(found.getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(found.getRole()).isEqualTo(UserRole.ENTRY_OPERATOR);
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(rawFirst("users").getString("role")).isEqualTo("ENTRY_OPERATOR"); // enums stored as strings
    }

    @Test
    void vehicleCategoryRoundTrip() {
        categories.save(new VehicleCategory("CAR", "Passenger car", EnumSet.of(SlotType.STANDARD, SlotType.LARGE)));

        VehicleCategory found = categories.findByCategoryName("CAR").orElseThrow();
        assertThat(found.getDescription()).isEqualTo("Passenger car");
        assertThat(found.getCompatibleSlotTypes()).containsExactlyInAnyOrder(SlotType.STANDARD, SlotType.LARGE);
        assertThat(categories.findByCompatibleSlotTypesContaining(SlotType.LARGE)).hasSize(1);
        assertThat(categories.findByCompatibleSlotTypesContaining(SlotType.MOTORCYCLE)).isEmpty();
    }

    @Test
    void parkingSlotRoundTrip() {
        slots.save(new ParkingSlot("A-01", SlotType.STANDARD, Set.of("CAR", "ELECTRIC_CAR"),
                SlotStatus.AVAILABLE, 0, "A", 1, NOW));

        ParkingSlot found = slots.findBySlotCode("A-01").orElseThrow();
        assertThat(found.getSupportedVehicleCategories()).containsExactlyInAnyOrder("CAR", "ELECTRIC_CAR");
        assertThat(found.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(found.getFloor()).isZero();
        assertThat(found.getRow()).isEqualTo("A");
        assertThat(found.getColumn()).isEqualTo(1);
        assertThat(found.getVersion()).isZero();
        assertThat(slots.findByStatusAndSupportedVehicleCategoriesContainingOrderByFloorAscRowAscColumnAsc(
                SlotStatus.AVAILABLE, "CAR")).hasSize(1);
    }

    @Test
    void parkingSessionRoundTrip() {
        sessions.save(ParkingSession.start("SP-20260101-000001", "WPCAB1234", "CAR", "A-01", NOW, "entry1"));

        ParkingSession found = sessions.findByTicketId("SP-20260101-000001").orElseThrow();
        assertThat(found.getVehicleNumber()).isEqualTo("WPCAB1234");
        assertThat(found.getVehicleCategory()).isEqualTo("CAR");
        assertThat(found.getParkingSlotCode()).isEqualTo("A-01");
        assertThat(found.getEntryTime()).isEqualTo(NOW);
        assertThat(found.getExitTime()).isNull();
        assertThat(found.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(found.getEntryOperator()).isEqualTo("entry1");
        assertThat(sessions.findByVehicleNumberAndStatus("WPCAB1234", SessionStatus.ACTIVE)).isPresent();
        assertThat(rawFirst("parkingSessions").getString("status")).isEqualTo("ACTIVE");
    }

    @Test
    void activityLogRoundTripNewestFirst() {
        activityLogs.save(new ActivityLog("entry1", ActivityAction.VEHICLE_ENTRY, "first", NOW.minusSeconds(10)));
        activityLogService.record("exit1", ActivityAction.VEHICLE_EXIT, "second");

        assertThat(activityLogService.recent(10))
                .extracting(a -> a.description())
                .containsExactly("second", "first");
        assertThat(activityLogService.recentForUser("entry1", 10)).hasSize(1);
    }

    private Document rawFirst(String collection) {
        return mongoTemplate.getCollection(collection).find().first();
    }
}
