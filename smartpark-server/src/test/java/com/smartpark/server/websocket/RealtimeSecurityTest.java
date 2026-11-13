package com.smartpark.server.websocket;

import com.jayway.jsonpath.DocumentContext;
import com.smartpark.server.support.AbstractApiIntegrationTest;
import com.smartpark.server.support.WsTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** WebSocket connections cannot be hijacked and end when the account's sign-ins are revoked. */
class RealtimeSecurityTest extends AbstractApiIntegrationTest {

    @BeforeEach
    void seed() {
        seedSampleData();
    }

    @Test
    void anotherOperatorCannotDisconnectAConnectionByReusingItsClientId() throws Exception {
        String adminClientId = UUID.randomUUID().toString();
        try (WsTestClient admin = WsTestClient.connect(port, ADMIN, "ADMIN", "clientId=" + adminClientId)) {
            admin.await("CONNECTION_ACK");

            // The admin's clientId is visible to everyone in presence events; an entry operator reuses it.
            try (WsTestClient attacker = WsTestClient.connect(port, ENTRY, "ENTRY-GATE-1", "clientId=" + adminClientId)) {
                attacker.await("CONNECTION_ACK");
                Thread.sleep(300);
                assertThat(admin.closedFuture()).isNotDone();          // admin still connected

                enter("STILLLIVE1", "CAR", "A-01").expect(201);
                assertThat(admin.receives("SLOT_OCCUPIED",
                        e -> "A-01".equals(e.read("$.payload.slot.slotCode")), Duration.ofSeconds(5))).isTrue();
            }
        }
    }

    @Test
    void deactivatingAnAccountClosesItsLiveConnectionsAndRefusesReconnects() throws Exception {
        String token = login("entry1", "Entry@123");
        WsTestClient entry = WsTestClient.connectWithToken(port, token, "ENTRY-GATE-1");
        entry.await("CONNECTION_ACK");

        post("/api/users/entry1/deactivate", ADMIN, null).expect(200);

        assertThat(entry.closedFuture().get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        getWithToken("/api/auth/me", token).expectProblem(401, "UNAUTHENTICATED");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> WsTestClient.connectWithToken(port, token, "ENTRY-GATE-1"))
                .rootCause().hasMessageContaining("401");
    }

    @Test
    void clientChosenTerminalNamesAreSanitisedBeforeOthersSeeThem() throws Exception {
        try (WsTestClient admin = WsTestClient.connect(port, ADMIN, "ADMIN", null)) {
            admin.await("CONNECTION_ACK");
            String evil = "GATE<script>\r\nFAKE LOG LINE" + "x".repeat(100);
            try (WsTestClient entry = WsTestClient.connect(port, ENTRY, evil, null)) {
                entry.await("CONNECTION_ACK");
                DocumentContext presence = admin.await("OPERATOR_CONNECTED",
                        e -> "entry1".equals(e.read("$.payload.connection.operator")), Duration.ofSeconds(5));
                String terminal = presence.read("$.payload.connection.terminal");
                assertThat(terminal).doesNotContain("<", ">", "\r", "\n").hasSizeLessThanOrEqualTo(40)
                        .startsWith("GATE_script_");
            }
        }
    }
}
