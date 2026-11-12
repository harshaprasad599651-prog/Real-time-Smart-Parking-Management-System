package com.smartpark.server.repository;

import com.smartpark.server.document.ParkingSlot;
import com.smartpark.server.document.SlotStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Read access and simple CRUD for parking slots.
 * <p>
 * Status changes that must be race-free (allocate, release, disable, enable) are
 * <b>not</b> done through this repository. {@code ParkingSlotService} and
 * {@code ParkingSessionService} do them as atomic conditional updates with MongoTemplate.
 */
public interface ParkingSlotRepository extends MongoRepository<ParkingSlot, String> {

    Optional<ParkingSlot> findBySlotCode(String slotCode);

    boolean existsBySlotCode(String slotCode);

    List<ParkingSlot> findAllByOrderByFloorAscRowAscColumnAsc();

    List<ParkingSlot> findByStatusOrderByFloorAscRowAscColumnAsc(SlotStatus status);

    /** Slots in the given status whose {@code supportedVehicleCategories} array contains the category. */
    List<ParkingSlot> findByStatusAndSupportedVehicleCategoriesContainingOrderByFloorAscRowAscColumnAsc(
            SlotStatus status, String category);

    long countByStatus(SlotStatus status);
}
