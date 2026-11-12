package com.smartpark.server.service;

import com.smartpark.server.document.ParkingSession;
import com.smartpark.server.document.ParkingSlot;
import com.smartpark.server.document.SessionStatus;
import com.smartpark.server.document.SlotStatus;
import com.smartpark.server.document.VehicleCategory;
import com.smartpark.server.dto.ParkingStatisticsDto;
import com.smartpark.server.dto.ParkingStatisticsDto.CategoryStatistics;
import com.smartpark.server.dto.ParkingStatisticsDto.FloorStatistics;
import com.smartpark.server.repository.ParkingSessionRepository;
import com.smartpark.server.repository.ParkingSlotRepository;
import com.smartpark.server.repository.VehicleCategoryRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** Read-only figures for the admin dashboard. */
@Service
public class ParkingStatisticsService {

    private final ParkingSlotRepository slotRepository;
    private final ParkingSessionRepository sessionRepository;
    private final VehicleCategoryRepository categoryRepository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock = Clock.systemDefaultZone();

    public ParkingStatisticsService(ParkingSlotRepository slotRepository, ParkingSessionRepository sessionRepository,
                                    VehicleCategoryRepository categoryRepository, MongoTemplate mongoTemplate) {
        this.slotRepository = slotRepository;
        this.sessionRepository = sessionRepository;
        this.categoryRepository = categoryRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public ParkingStatisticsDto statistics() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant startOfToday = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();

        // Slots: a car park has tens to a few hundred slots, so counting in memory is simplest.
        List<ParkingSlot> slots = slotRepository.findAll();
        long available = count(slots, s -> s.getStatus() == SlotStatus.AVAILABLE);
        long occupied = count(slots, s -> s.getStatus() == SlotStatus.OCCUPIED);
        long disabled = count(slots, s -> s.getStatus() == SlotStatus.DISABLED);
        long usable = slots.size() - disabled;
        double occupancy = usable == 0 ? 0.0 : Math.round(occupied * 1000.0 / usable) / 10.0;

        // Sessions
        long active = sessionRepository.countByStatus(SessionStatus.ACTIVE);
        long entriesToday = mongoTemplate.count(query(where(ParkingSession.F_ENTRY_TIME).gte(startOfToday)),
                ParkingSession.class);
        Query exitedToday = query(where(ParkingSession.F_STATUS).is(SessionStatus.COMPLETED)
                .and(ParkingSession.F_EXIT_TIME).gte(startOfToday));
        exitedToday.fields().include(ParkingSession.F_ENTRY_TIME, ParkingSession.F_EXIT_TIME);
        List<ParkingSession> completedToday = mongoTemplate.find(exitedToday, ParkingSession.class);
        Double averageStay = completedToday.isEmpty() ? null : Math.round(completedToday.stream()
                .mapToLong(s -> Duration.between(s.getEntryTime(), s.getExitTime()).toSeconds())
                .average().orElse(0) / 6.0) / 10.0; // minutes, 1 decimal

        // Per category
        List<CategoryStatistics> categories = new ArrayList<>();
        for (VehicleCategory c : categoryRepository.findAllByOrderByCategoryNameAsc()) {
            String name = c.getCategoryName();
            Predicate<ParkingSlot> accepts = s -> s.getSupportedVehicleCategories().contains(name);
            long parked = mongoTemplate.count(query(where(ParkingSession.F_STATUS).is(SessionStatus.ACTIVE)
                    .and(ParkingSession.F_VEHICLE_CATEGORY).is(name)), ParkingSession.class);
            categories.add(new CategoryStatistics(name,
                    count(slots, accepts.and(s -> s.getStatus() != SlotStatus.DISABLED)),
                    count(slots, accepts.and(s -> s.getStatus() == SlotStatus.AVAILABLE)),
                    parked));
        }

        // Per floor
        Map<Integer, List<ParkingSlot>> byFloor = new TreeMap<>();
        slots.forEach(s -> byFloor.computeIfAbsent(s.getFloor(), f -> new ArrayList<>()).add(s));
        List<FloorStatistics> floors = byFloor.entrySet().stream().map(e -> new FloorStatistics(e.getKey(),
                e.getValue().size(),
                count(e.getValue(), s -> s.getStatus() == SlotStatus.AVAILABLE),
                count(e.getValue(), s -> s.getStatus() == SlotStatus.OCCUPIED),
                count(e.getValue(), s -> s.getStatus() == SlotStatus.DISABLED))).toList();

        return new ParkingStatisticsDto(now, slots.size(), available, occupied, disabled, occupancy,
                active, entriesToday, completedToday.size(), averageStay, categories, floors);
    }

    private static long count(List<ParkingSlot> slots, Predicate<ParkingSlot> predicate) {
        return slots.stream().filter(predicate).count();
    }
}
