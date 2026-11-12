package com.smartpark.server.websocket;

import com.jayway.jsonpath.DocumentContext;
import com.smartpark.server.support.AbstractApiIntegrationTest;
import com.smartpark.server.support.WsTestClient;
import org.junit.jupiter.api.Test;

import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end WebSocket tests: several Java clients connect to the real server
 * (embedded Tomcat on a random port) while changes are made through the REST API.
 */
class RealtimeWebSocketTest extends AbstractApiIntegrationTest {

    private static final Duration SHORT = Duration.ofSeconds(1);
    private static final Duration WAIT = Duration.ofSeconds(10);

    private WsTestClient connect(String operator, String terminal) throws Exception {
        return WsTestClient.connect(port, operator, terminal, null);
    }

    // ---------------------------------------------------------------- connecting

    @Test
    void clientReceivesConnectionAckAndAppearsInStatus() throws Exception {
        try (WsTestClient entry = connect(ENTRY, "ENTRY-GATE-1")) {
            DocumentContext ack = entry.await("CONNECTION_ACK");

            assertThat(ack.<Integer>read("$.sequence")).isZero();
            assertThat(ack.<String>read("$.payload.serverInstanceId")).isNotBlank();
            assertThat(ack.<Boolean>read("$.payload.resyncRequired")).isFalse();
            assertThat(ack.<Integer>read("$.payload.heartbeatSeconds")).isEqualTo(15);

            Response status = get("/api/realtime/status").expect(200);
            assertThat((List<String>) status.json("$.clients[?(@.operator=='entry1')].terminal")).contains("ENTRY-GATE-1");
            assertThat((String) status.json("$.serverInstanceId")).isEqualTo(ack.read("$.payload.serverInstanceId"));
        }
    }

    @Test
    void handshakeIsRefusedForUnknownOrMissingOperator() {
        for (String operator : new String[]{"ghost", null}) {
            assertThatThrownBy(() -> WsTestClient.connect(port, operator, "X", null))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOf(WebSocketHandshakeException.class)
                    .satisfies(e -> assertThat(((WebSocketHandshakeException) e).getResponse().statusCode()).isEqualTo(403));
        }
    }

    // ------------------------------------------------------------ broadcasting

    @Test
    void entryAndExitAreBroadcastToEveryConnectedClient() throws Exception {
        try (WsTestClient admin = connect(ADMIN, "ADMIN");
             WsTestClient entry = connect(ENTRY, "ENTRY-GATE-1");
             WsTestClient exit = connect(EXIT, "EXIT-GATE-1")) {
            List<WsTestClient> all = List.of(admin, entry, exit);
            for (WsTestClient c : all) {
                c.await("CONNECTION_ACK");
            }

            String ticket = enter("WP CAB-1234", "CAR", "A-01").expect(201).json("$.ticketId");

            String occupiedId = null;
            for (WsTestClient c : all) {
                DocumentContext occupied = c.await("SLOT_OCCUPIED", forVehicle("WPCAB1234"), WAIT);
                DocumentContext entered = c.await("VEHICLE_ENTERED", d -> ticket.equals(d.read("$.payload.ticketId")), WAIT);
                assertThat(occupied.<String>read("$.payload.slot.slotCode")).isEqualTo("A-01");
                assertThat(occupied.<String>read("$.payload.slot.status")).isEqualTo("OCCUPIED");
                assertThat(occupied.<String>read("$.actor")).isEqualTo(ENTRY);
                assertThat(occupied.<String>read("$.terminal")).isEqualTo("ENTRY-GATE-1");
                assertThat(entered.<Integer>read("$.sequence")).isEqualTo(occupied.<Integer>read("$.sequence") + 1);
                // every client receives the very same event
                if (occupiedId == null) {
                    occupiedId = occupied.read("$.eventId");
                }
                assertThat(occupied.<String>read("$.eventId")).isEqualTo(occupiedId);
            }

            leave("WPCAB1234").expect(200);

            for (WsTestClient c : all) {
                DocumentContext released = c.await("SLOT_RELEASED", forVehicle("WPCAB1234"), WAIT);
                DocumentContext exited = c.await("VEHICLE_EXITED", d -> ticket.equals(d.read("$.payload.ticketId")), WAIT);
                assertThat(released.<String>read("$.payload.slot.status")).isEqualTo("AVAILABLE");
                assertThat(released.<String>read("$.payload.reason")).isEqualTo("VEHICLE_EXIT");
                assertThat(exited.<String>read("$.payload.status")).isEqualTo("COMPLETED");
                assertThat(exited.<String>read("$.actor")).isEqualTo(EXIT);
            }
        }
    }

    @Test
    void rejectedEntryBroadcastsNothing() throws Exception {
        enter("AAA111", "CAR", "A-01").expect(201);
        try (WsTestClient admin = connect(ADMIN, "ADMIN")) {
            admin.await("CONNECTION_ACK");

            enter("BBB222", "CAR", "A-01").expect(409);

            assertThat(admin.receives("SLOT_OCCUPIED", forVehicle("BBB222"), SHORT)).isFalse();
            assertThat(admin.received()).noneMatch(d -> "VEHICLE_ENTERED".equals(d.read("$.type")));
        }
    }

    @Test
    void mapChangesAreBroadcast() throws Exception {
        try (WsTestClient entry = connect(ENTRY, "ENTRY-GATE-1")) {
            entry.await("CONNECTION_ACK");

            post("/api/slots/A-05/disable", ADMIN, null).expect(200);
            DocumentContext disabled = entry.await("PARKING_MAP_UPDATED",
                    d -> "SLOT_DISABLED".equals(d.read("$.payload.reason")), WAIT);
            assertThat(disabled.<String>read("$.payload.slot.slotCode")).isEqualTo("A-05");
            assertThat(disabled.<String>read("$.payload.slot.status")).isEqualTo("DISABLED");

            put("/api/categories/VAN", ADMIN, """
                    {"compatibleSlotTypes": ["LARGE", "STANDARD"]}""").expect(200);
            DocumentContext category = entry.await("PARKING_MAP_UPDATED",
                    d -> "CATEGORY_UPDATED".equals(d.read("$.payload.reason")), WAIT);
            assertThat(category.<String>read("$.payload.categoryName")).isEqualTo("VAN");
            assertThat((Object) category.read("$.payload.slot")).isNull(); // null = reload the whole map
        }
    }

    // ----------------------------------------------------------------- presence

    @Test
    void adminSeesOperatorsConnectAndDisconnect() throws Exception {
        try (WsTestClient admin = connect(ADMIN, "ADMIN")) {
            admin.await("CONNECTION_ACK");

            WsTestClient exit = connect(EXIT, "EXIT-GATE-1");
            DocumentContext connected = admin.await("OPERATOR_CONNECTED",
                    d -> EXIT.equals(d.read("$.payload.connection.operator")), WAIT);
            assertThat(connected.<String>read("$.payload.connection.terminal")).isEqualTo("EXIT-GATE-1");
            assertThat(connected.<String>read("$.payload.connection.role")).isEqualTo("EXIT_OPERATOR");

            exit.abort(); // network failure: no close handshake
            DocumentContext disconnected = admin.await("OPERATOR_DISCONNECTED",
                    d -> EXIT.equals(d.read("$.payload.connection.operator")), WAIT);
            assertThat(disconnected.<String>read("$.payload.closeReason")).isNotBlank();
        }
    }

    @Test
    void reconnectingClientReplacesItsOldConnection() throws Exception {
        String clientId = UUID.randomUUID().toString();
        WsTestClient first = WsTestClient.connect(port, ENTRY, "ENTRY-GATE-1", "clientId=" + clientId);
        first.await("CONNECTION_ACK");

        try (WsTestClient second = WsTestClient.connect(port, ENTRY, "ENTRY-GATE-1", "clientId=" + clientId)) {
            second.await("CONNECTION_ACK");

            assertThat(first.closedFuture().get(5, TimeUnit.SECONDS)).isEqualTo(1008); // policy violation: replaced
            Thread.sleep(200);
            assertThat((List<?>) get("/api/realtime/status").json("$.clients[?(@.clientId=='" + clientId + "')]"))
                    .hasSize(1);
        }
    }

    // ------------------------------------------------------- reconnect & replay

    @Test
    void reconnectReplaysMissedEventsInOrderWithoutGaps() throws Exception {
        String clientId = UUID.randomUUID().toString();
        WsTestClient before = WsTestClient.connect(port, ENTRY, "ENTRY-GATE-1", "clientId=" + clientId);
        DocumentContext ack = before.await("CONNECTION_ACK");
        String instance = ack.read("$.payload.serverInstanceId");
        int lastSeen = ack.read("$.payload.currentSequence");

        before.abort();                                      // connection lost ...
        enter("MISSED1", "CAR", "A-02").expect(201);         // ... while things happen
        enter("MISSED2", "CAR", "A-03").expect(201);

        try (WsTestClient after = WsTestClient.connect(port, ENTRY, "ENTRY-GATE-1",
                "clientId=" + clientId + "&since=" + lastSeen + "&instance=" + instance)) {
            DocumentContext reAck = after.await("CONNECTION_ACK");
            assertThat(reAck.<Boolean>read("$.payload.resyncRequired")).isFalse();
            int replayed = reAck.read("$.payload.replayedEvents");
            assertThat(replayed).isGreaterThanOrEqualTo(4); // 2 x (SLOT_OCCUPIED + VEHICLE_ENTERED), maybe more

            after.await("VEHICLE_ENTERED", d -> "MISSED2".equals(d.read("$.payload.vehicleNumber")), WAIT);
            List<Integer> sequences = after.received().stream()
                    .filter(d -> d.<Integer>read("$.sequence") > 0)
                    .map(d -> d.<Integer>read("$.sequence")).toList();
            assertThat(sequences.getFirst()).isEqualTo(lastSeen + 1);  // nothing missed
            for (int i = 1; i < sequences.size(); i++) {
                assertThat(sequences.get(i)).isEqualTo(sequences.get(i - 1) + 1); // no gaps, no duplicates
            }
        }
    }

    @Test
    void reconnectToDifferentServerInstanceRequiresResync() throws Exception {
        try (WsTestClient c = WsTestClient.connect(port, ENTRY, "ENTRY-GATE-1", "since=5&instance=oldserver")) {
            DocumentContext ack = c.await("CONNECTION_ACK");
            assertThat(ack.<Boolean>read("$.payload.resyncRequired")).isTrue();
            assertThat(ack.<String>read("$.payload.resyncReason")).isEqualTo("SERVER_RESTARTED");
            assertThat(ack.<Integer>read("$.payload.replayedEvents")).isZero();
        }
    }

    @Test
    void applicationLevelPingGetsPong() throws Exception {
        try (WsTestClient c = connect(EXIT, "EXIT-GATE-1")) {
            c.await("CONNECTION_ACK");
            c.sendText("PING");
            assertThat(c.await("PONG").<Integer>read("$.sequence")).isZero();
        }
    }

    private static java.util.function.Predicate<DocumentContext> forVehicle(String vehicle) {
        return d -> vehicle.equals(d.read("$.payload.vehicleNumber"));
    }
}
