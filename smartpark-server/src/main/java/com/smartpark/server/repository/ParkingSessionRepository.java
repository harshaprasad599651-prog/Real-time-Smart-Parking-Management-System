package com.smartpark.server.repository;

import com.smartpark.server.document.ParkingSession;
import com.smartpark.server.document.SessionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ParkingSessionRepository extends MongoRepository<ParkingSession, String> {

    Optional<ParkingSession> findByTicketId(String ticketId);

    Optional<ParkingSession> findByVehicleNumberAndStatus(String vehicleNumber, SessionStatus status);

    Optional<ParkingSession> findByParkingSlotCodeAndStatus(String parkingSlotCode, SessionStatus status);

    boolean existsByVehicleNumberAndStatus(String vehicleNumber, SessionStatus status);

    List<ParkingSession> findByStatusOrderByEntryTimeDesc(SessionStatus status);

    List<ParkingSession> findByVehicleNumberOrderByEntryTimeDesc(String vehicleNumber);

    List<ParkingSession> findByEntryTimeBetweenOrderByEntryTimeDesc(Instant from, Instant to);

    long countByStatus(SessionStatus status);
}
