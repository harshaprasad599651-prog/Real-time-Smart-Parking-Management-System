package com.smartpark.server.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanAddressReporterTest {

    @Test
    void wildcardBindAddressesListEveryLanAdapter() {
        assertThat(LanAddressReporter.isWildcard("0.0.0.0")).isTrue();
        assertThat(LanAddressReporter.isWildcard("")).isTrue();
        assertThat(LanAddressReporter.isWildcard("192.168.1.10")).isFalse();
    }

    @Test
    void virtualAdaptersAreNotSuggestedToClients() {
        assertThat(LanAddressReporter.isVirtualAdapter("VirtualBox Host-Only Ethernet Adapter")).isTrue();
        assertThat(LanAddressReporter.isVirtualAdapter("vEthernet (WSL)")).isTrue();
        assertThat(LanAddressReporter.isVirtualAdapter("VMware Virtual Ethernet Adapter for VMnet8")).isTrue();
        assertThat(LanAddressReporter.isVirtualAdapter("Intel(R) Wi-Fi 6 AX201 160MHz")).isFalse();
        assertThat(LanAddressReporter.isVirtualAdapter("Realtek PCIe GbE Family Controller")).isFalse();
    }

    @Test
    void reportedAddressesArePrivateIpv4() {
        assertThat(LanAddressReporter.lanAddresses())
                .allSatisfy(a -> assertThat(a).matches("(10|172|192)\\.\\d+\\.\\d+\\.\\d+"));
    }
}
