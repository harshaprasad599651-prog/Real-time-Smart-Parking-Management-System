package com.smartpark.server.bootstrap;

import com.smartpark.server.document.ActivityAction;
import com.smartpark.server.document.SlotType;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.dto.CreateParkingSlotRequest;
import com.smartpark.server.dto.CreateUserRequest;
import com.smartpark.server.dto.CreateVehicleCategoryRequest;
import com.smartpark.server.repository.ParkingSlotRepository;
import com.smartpark.server.repository.UserRepository;
import com.smartpark.server.repository.VehicleCategoryRepository;
import com.smartpark.server.service.ActivityLogService;
import com.smartpark.server.service.ParkingSlotService;
import com.smartpark.server.service.UserService;
import com.smartpark.server.service.VehicleCategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Creates sample data on first start. Each collection is seeded only if it is
 * empty, so restarting the server never duplicates or overwrites data.
 *
 * <pre>
 *  Users       admin (ADMIN), entry1 (ENTRY_OPERATOR), exit1 (EXIT_OPERATOR)
 *  Categories  CAR, MOTORCYCLE, VAN, ELECTRIC_CAR, ACCESSIBLE
 *  Slots       Floor 0: A-01..A-08 STANDARD
 *                       B-01..B-04 LARGE, B-05..B-06 EV_CHARGING, B-07..B-08 ACCESSIBLE
 *                       M-01..M-06 MOTORCYCLE
 *              Floor 1: C-01..C-08 STANDARD                          (30 slots)
 * </pre>
 * Every slot's {@code supportedVehicleCategories} is derived from the categories'
 * {@code compatibleSlotTypes}.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String ACTOR = ActivityLogService.SYSTEM_USER;

    public record SeedResult(int users, int categories, int slots) {
        public boolean nothingCreated() {
            return users == 0 && categories == 0 && slots == 0;
        }
    }

    private final boolean enabled;
    private final String adminPassword;
    private final String entryPassword;
    private final String exitPassword;
    private final UserRepository userRepository;
    private final VehicleCategoryRepository categoryRepository;
    private final ParkingSlotRepository slotRepository;
    private final UserService userService;
    private final VehicleCategoryService categoryService;
    private final ParkingSlotService slotService;
    private final ActivityLogService activityLog;

    public DataSeeder(@Value("${smartpark.seed.enabled:true}") boolean enabled,
                      @Value("${smartpark.seed.admin-password}") String adminPassword,
                      @Value("${smartpark.seed.entry-password}") String entryPassword,
                      @Value("${smartpark.seed.exit-password}") String exitPassword,
                      UserRepository userRepository,
                      VehicleCategoryRepository categoryRepository,
                      ParkingSlotRepository slotRepository,
                      UserService userService,
                      VehicleCategoryService categoryService,
                      ParkingSlotService slotService,
                      ActivityLogService activityLog) {
        this.enabled = enabled;
        this.adminPassword = adminPassword;
        this.entryPassword = entryPassword;
        this.exitPassword = exitPassword;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.slotRepository = slotRepository;
        this.userService = userService;
        this.categoryService = categoryService;
        this.slotService = slotService;
        this.activityLog = activityLog;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Sample data seeding is disabled (smartpark.seed.enabled=false)");
            return;
        }
        SeedResult result = seed();
        if (result.nothingCreated()) {
            log.info("Sample data already present - nothing seeded");
        } else {
            log.info("Seeded sample data: {} users, {} categories, {} slots",
                    result.users(), result.categories(), result.slots());
        }
    }

    public SeedResult seed() {
        int users = seedUsers();
        int categories = seedCategories(); // before slots: slots derive their categories from these
        int slots = seedSlots();
        SeedResult result = new SeedResult(users, categories, slots);
        if (!result.nothingCreated()) {
            activityLog.record(ACTOR, ActivityAction.DATA_SEEDED, "Seeded " + result);
        }
        return result;
    }

    private int seedUsers() {
        if (userRepository.count() > 0) {
            return 0;
        }
        userService.createUser(new CreateUserRequest("admin", adminPassword, UserRole.ADMIN), ACTOR);
        userService.createUser(new CreateUserRequest("entry1", entryPassword, UserRole.ENTRY_OPERATOR), ACTOR);
        userService.createUser(new CreateUserRequest("exit1", exitPassword, UserRole.EXIT_OPERATOR), ACTOR);
        return 3;
    }

    private int seedCategories() {
        if (categoryRepository.count() > 0) {
            return 0;
        }
        category("CAR", "Standard passenger car", EnumSet.of(SlotType.STANDARD, SlotType.LARGE));
        category("MOTORCYCLE", "Motorcycles and scooters", EnumSet.of(SlotType.MOTORCYCLE));
        category("VAN", "Vans, SUVs and light trucks", EnumSet.of(SlotType.LARGE));
        category("ELECTRIC_CAR", "Electric vehicle (may use charging bays)",
                EnumSet.of(SlotType.EV_CHARGING, SlotType.STANDARD, SlotType.LARGE));
        category("ACCESSIBLE", "Vehicle with a disability permit", EnumSet.of(SlotType.ACCESSIBLE, SlotType.STANDARD));
        return 5;
    }

    private int seedSlots() {
        if (slotRepository.count() > 0) {
            return 0;
        }
        int created = 0;
        created += row(0, "A", 1, 8, SlotType.STANDARD);
        created += row(0, "B", 1, 4, SlotType.LARGE);
        created += row(0, "B", 5, 6, SlotType.EV_CHARGING);
        created += row(0, "B", 7, 8, SlotType.ACCESSIBLE);
        created += row(0, "M", 1, 6, SlotType.MOTORCYCLE);
        created += row(1, "C", 1, 8, SlotType.STANDARD);
        return created;
    }

    private void category(String name, String description, Set<SlotType> slotTypes) {
        categoryService.createCategory(new CreateVehicleCategoryRequest(name, description, slotTypes), ACTOR);
    }

    private int row(int floor, String row, int fromColumn, int toColumn, SlotType type) {
        for (int col = fromColumn; col <= toColumn; col++) {
            String code = "%s-%02d".formatted(row, col);
            // null categories -> derived from VehicleCategory.compatibleSlotTypes
            slotService.createSlot(new CreateParkingSlotRequest(code, type, null, floor, row, col), ACTOR);
        }
        return toColumn - fromColumn + 1;
    }
}
