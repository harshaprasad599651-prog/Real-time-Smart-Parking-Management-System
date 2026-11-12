package com.smartpark.server.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test, no MongoDB needed. */
class NormalizerTest {

    @Test
    void vehicleNumbersIgnoreCaseSpacesAndHyphens() {
        assertThat(Normalizer.vehicleNumber(" wp cab-1234 ")).isEqualTo("WPCAB1234");
        assertThat(Normalizer.vehicleNumber("WP-CAB 1234")).isEqualTo("WPCAB1234");
    }

    @Test
    void rejectsInvalidVehicleNumbers() {
        assertThatThrownBy(() -> Normalizer.vehicleNumber("A")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Normalizer.vehicleNumber("AB#12")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Normalizer.vehicleNumber(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalisesCategorySlotAndUsername() {
        assertThat(Normalizer.categoryName(" electric  car ")).isEqualTo("ELECTRIC_CAR");
        assertThat(Normalizer.slotCode(" a-01 ")).isEqualTo("A-01");
        assertThat(Normalizer.username(" Admin ")).isEqualTo("admin");
    }
}
