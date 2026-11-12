package com.smartpark.client.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientConfigTest {

    @AfterEach
    void clearOverrides() {
        System.clearProperty("smartpark.server.host");
        System.clearProperty("smartpark.server.port");
    }

    @Test
    void buildsServerUrisFromHostAndPort() {
        ClientConfig config = new ClientConfig("192.168.1.10", 8080, "ENTRY-GATE-1");

        assertEquals("http://192.168.1.10:8080", config.httpBaseUri().toString());
        assertEquals("ws://192.168.1.10:8080/ws", config.webSocketUri().toString());
    }

    @Test
    void systemPropertiesOverrideDefaults() {
        System.setProperty("smartpark.server.host", "10.0.0.5");
        System.setProperty("smartpark.server.port", "9090");

        ClientConfig config = ClientConfig.load();

        assertEquals("10.0.0.5", config.serverHost());
        assertEquals(9090, config.serverPort());
    }

    @Test
    void blankSystemPropertyIsIgnored() {
        System.setProperty("smartpark.server.port", "");

        assertEquals(8080, ClientConfig.load().serverPort());
    }
}
