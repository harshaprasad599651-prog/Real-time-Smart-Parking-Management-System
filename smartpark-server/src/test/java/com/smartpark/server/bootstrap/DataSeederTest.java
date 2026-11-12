package com.smartpark.server.bootstrap;

import com.smartpark.server.document.SlotStatus;
import com.smartpark.server.document.UserRole;
import com.smartpark.server.dto.UserDto;
import com.smartpark.server.repository.ParkingSlotRepository;
import com.smartpark.server.service.ParkingSlotService;
import com.smartpark.server.service.UserService;
import com.smartpark.server.service.VehicleCategoryService;
import com.smartpark.server.support.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DataSeederTest extends AbstractMongoIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private VehicleCategoryService categoryService;
    @Autowired private ParkingSlotService slotService;
    @Autowired private ParkingSlotRepository slotRepository;

    @Test
    void seedsUsersCategoriesAndSlotsOnce() {
        DataSeeder.SeedResult first = dataSeeder.seed();
        DataSeeder.SeedResult second = dataSeeder.seed();

        assertThat(first).isEqualTo(new DataSeeder.SeedResult(3, 5, 30));
        assertThat(second.nothingCreated()).isTrue();
        assertThat(slotRepository.count()).isEqualTo(30);
        assertThat(slotService.occupancySummary().available()).isEqualTo(30);
    }

    @Test
    void createsOneAccountPerRole() {
        dataSeeder.seed();

        assertThat(userService.listUsers()).extracting(UserDto::username, UserDto::role)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("admin", UserRole.ADMIN),
                        org.assertj.core.groups.Tuple.tuple("entry1", UserRole.ENTRY_OPERATOR),
                        org.assertj.core.groups.Tuple.tuple("exit1", UserRole.EXIT_OPERATOR));
        assertThat(userService.authenticate("entry1", "Entry@123")).isPresent();
        assertThat(userService.authenticate("exit1", "Exit@123")).isPresent();
    }

    @Test
    void derivesSupportedCategoriesFromSlotTypes() {
        dataSeeder.seed();

        assertThat(categoryService.listCategories()).hasSize(5);
        assertThat(slotService.getSlot("A-01").supportedVehicleCategories())
                .containsExactlyInAnyOrder("CAR", "ELECTRIC_CAR", "ACCESSIBLE");
        assertThat(slotService.getSlot("B-01").supportedVehicleCategories())
                .containsExactlyInAnyOrder("CAR", "VAN", "ELECTRIC_CAR");
        assertThat(slotService.getSlot("B-05").supportedVehicleCategories()).containsExactly("ELECTRIC_CAR");
        assertThat(slotService.getSlot("B-07").supportedVehicleCategories()).containsExactly("ACCESSIBLE");
        assertThat(slotService.getSlot("M-01").supportedVehicleCategories()).containsExactly("MOTORCYCLE");
        assertThat(slotService.getSlot("C-08").floor()).isEqualTo(1);
        assertThat(slotService.getSlot("C-08").status()).isEqualTo(SlotStatus.AVAILABLE);
    }
}
