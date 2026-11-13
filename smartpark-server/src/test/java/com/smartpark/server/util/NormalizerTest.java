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

    @Test
    void freeTextDropsControlAndInvisibleCharactersAndIsCapped() {
        assertThat(Normalizer.freeText("  line one\r\nline\ttwo‮​ end ", 200)).isEqualTo("line one line two end");
        assertThat(Normalizer.freeText(" \n\t ", 200)).isNull();
        assertThat(Normalizer.freeText(null, 200)).isNull();
        assertThat(Normalizer.freeText("abcdefghij", 5)).isEqualTo("abcde");
    }

    @Test
    void labelsKeepOnlySafeCharacters() {
        assertThat(Normalizer.label("ENTRY-GATE-1", 40, "UNKNOWN")).isEqualTo("ENTRY-GATE-1");
        assertThat(Normalizer.label("<b>gate</b>;drop", 40, "UNKNOWN")).isEqualTo("_b_gate__b__drop");
        assertThat(Normalizer.label("", 40, "UNKNOWN")).isEqualTo("UNKNOWN");
        assertThat(Normalizer.label("x".repeat(100), 40, "UNKNOWN")).hasSize(40);
    }

    @Test
    void clientIdsMustLookLikeIds() {
        assertThat(Normalizer.clientId("6a9ee307-0ab3-4b2b-a002-02cecd1d5770")).isNotNull();
        assertThat(Normalizer.clientId("short")).isNull();
        assertThat(Normalizer.clientId("../../etc")).isNull();
        assertThat(Normalizer.clientId(null)).isNull();
    }
}
