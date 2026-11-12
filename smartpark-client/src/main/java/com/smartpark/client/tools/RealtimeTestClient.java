package com.smartpark.client.tools;

import com.smartpark.client.config.ClientConfig;
import com.smartpark.client.net.ServerApiClient;
import com.smartpark.client.realtime.ConnectionAck;
import com.smartpark.client.realtime.ConnectionState;
import com.smartpark.client.realtime.RealtimeClient;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.realtime.RealtimeListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/**
 * Command-line client for checking SmartPark's real-time WebSocket without the JavaFX UI.
 *
 * <pre>
 * listen  Connect as one terminal and print every event until Enter is pressed.
 *         Run it on several PCs at once to watch the broadcasts arrive everywhere.
 *
 * demo    Connect several terminals at once (admin, entry, exit, ...), then:
 *           1. park a vehicle through the REST API   -> every terminal must get SLOT_OCCUPIED + VEHICLE_ENTERED
 *           2. cut the exit terminal's connection     (simulated network failure)
 *           3. release the vehicle through REST        -> every terminal, including the reconnected one,
 *                                                         must get SLOT_RELEASED + VEHICLE_EXITED exactly once
 *         Prints a PASS/FAIL summary.
 *
 * Options: --host H  --port P  --operator U  --terminal T  --clients N (demo, default 3)
 * Run:     mvnw -pl smartpark-client compile exec:java -Dexec.args="demo --clients 5"
 * </pre>
 */
public final class RealtimeTestClient {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration WAIT = Duration.ofSeconds(30);

    private RealtimeTestClient() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parse(args);
        ClientConfig defaults = ClientConfig.load();
        String host = opts.getOrDefault("host", defaults.serverHost());
        int port = Integer.parseInt(opts.getOrDefault("port", String.valueOf(defaults.serverPort())));
        ClientConfig config = new ClientConfig(host, port, defaults.terminalName());

        switch (opts.getOrDefault("mode", "listen")) {
            case "listen" -> listen(config, opts.getOrDefault("operator", "admin"),
                    opts.getOrDefault("terminal", defaults.terminalName()));
            case "demo" -> demo(config, Integer.parseInt(opts.getOrDefault("clients", "3")));
            default -> throw new IllegalArgumentException("Unknown mode; use 'listen' or 'demo'");
        }
    }

    // ------------------------------------------------------------------ listen

    private static void listen(ClientConfig config, String operator, String terminal) throws Exception {
        print("Connecting to " + config.webSocketUri() + " as " + operator + " (" + terminal + ")");
        print("Press Enter to quit.");
        try (RealtimeClient client = new RealtimeClient(config.webSocketUri(), operator, terminal)) {
            client.addListener(new PrintingListener(terminal));
            client.connect();
            System.in.read();
            print("Stats: " + client.stats());
        }
    }

    // -------------------------------------------------------------------- demo

    private static void demo(ClientConfig config, int clientCount) throws Exception {
        URI ws = config.webSocketUri();
        print("=== SmartPark real-time demo: " + clientCount + " clients -> " + ws + " ===");

        List<Terminal> terminals = new ArrayList<>();
        String[][] roles = {{"admin", "ADMIN-DASHBOARD"}, {"entry1", "ENTRY-GATE-1"}, {"exit1", "EXIT-GATE-1"}};
        for (int i = 0; i < clientCount; i++) {
            String operator = i < roles.length ? roles[i][0] : "admin";
            String terminal = i < roles.length ? roles[i][1] : "MONITOR-" + (i - roles.length + 1);
            terminals.add(new Terminal(new RealtimeClient(ws, operator, terminal)));
        }

        try (ServerApiClient api = new ServerApiClient(config)) {
            terminals.forEach(t -> t.client.connect());
            check("all " + clientCount + " clients connected",
                    waitFor(() -> terminals.stream().allMatch(t -> t.acknowledged)));

            // 1. Park a vehicle
            String vehicle = "DEMO" + ThreadLocalRandom.current().nextInt(1000, 10000);
            HttpResponse<String> slots = api.get("/api/slots/available?category=CAR").join();
            JsonNode available = JSON.readTree(slots.body());
            if (available.isEmpty()) {
                throw new IllegalStateException("No AVAILABLE slot for CAR - free one and try again");
            }
            String slot = available.get(0).get("slotCode").asString();
            print(">>> REST POST /api/parking/entry  " + vehicle + " -> " + slot + "  (as entry1)");
            HttpResponse<String> entry = api.post("/api/parking/entry", "entry1",
                    "{\"vehicleNumber\":\"" + vehicle + "\",\"categoryName\":\"CAR\",\"slotCode\":\"" + slot + "\"}").join();
            check("entry accepted (HTTP " + entry.statusCode() + ")", entry.statusCode() == 201);
            check("every client received SLOT_OCCUPIED + VEHICLE_ENTERED for " + vehicle,
                    waitFor(() -> terminals.stream().allMatch(t -> t.has("SLOT_OCCUPIED", vehicle)
                            && t.has("VEHICLE_ENTERED", vehicle))));

            // 2. Network failure on the exit terminal
            Terminal victim = terminals.get(Math.min(2, terminals.size() - 1));
            print(">>> Simulating network failure on " + victim.client.terminal());
            victim.client.simulateNetworkFailure();

            // 3. Release the vehicle while that terminal is offline
            print(">>> REST POST /api/parking/exit   " + vehicle + "  (as exit1)");
            HttpResponse<String> exit = api.post("/api/parking/exit", "exit1",
                    "{\"vehicleNumber\":\"" + vehicle + "\"}").join();
            check("exit accepted (HTTP " + exit.statusCode() + ")", exit.statusCode() == 200);
            check(victim.client.terminal() + " reconnected",
                    waitFor(() -> victim.client.state() == ConnectionState.CONNECTED && victim.client.stats().reconnects() > 0));
            check("every client received SLOT_RELEASED + VEHICLE_EXITED for " + vehicle,
                    waitFor(() -> terminals.stream().allMatch(t -> t.has("SLOT_RELEASED", vehicle)
                            && t.has("VEHICLE_EXITED", vehicle))));
            check("no client processed any event twice", terminals.stream().noneMatch(t -> t.processedTwice));

            Thread.sleep(500);
            summary(terminals);
        } finally {
            terminals.forEach(t -> t.client.close());
        }
        if (failures > 0) {
            throw new IllegalStateException(failures + " check(s) failed");
        }
        print("RESULT: PASS");
    }

    /** One simulated terminal and what it has received. */
    private static final class Terminal implements RealtimeListener {
        final RealtimeClient client;
        final Set<String> seenEventIds = ConcurrentHashMap.newKeySet();
        final Map<String, Set<String>> vehiclesByType = new ConcurrentHashMap<>();
        volatile boolean acknowledged;
        volatile boolean processedTwice;
        final PrintingListener printer;

        Terminal(RealtimeClient client) {
            this.client = client;
            this.printer = new PrintingListener(client.terminal());
            client.addListener(this);
        }

        boolean has(String type, String vehicle) {
            return vehiclesByType.getOrDefault(type, Set.of()).contains(vehicle);
        }

        @Override
        public void onStateChanged(ConnectionState state, String detail) {
            printer.onStateChanged(state, detail);
        }

        @Override
        public void onConnected(ConnectionAck ack) {
            acknowledged = true;
            printer.onConnected(ack);
        }

        @Override
        public void onEvent(RealtimeEvent event) {
            if (!seenEventIds.add(event.eventId())) {
                processedTwice = true;
            }
            String vehicle = event.payloadText("vehicleNumber");
            if (vehicle != null) {
                vehiclesByType.computeIfAbsent(event.type(), k -> ConcurrentHashMap.newKeySet()).add(vehicle);
            }
            printer.onEvent(event);
        }
    }

    /** Prints connection changes and events in a readable one-line format. */
    private record PrintingListener(String terminal) implements RealtimeListener {

        @Override
        public void onStateChanged(ConnectionState state, String detail) {
            print("[" + terminal + "] state " + state + " - " + detail);
        }

        @Override
        public void onConnected(ConnectionAck ack) {
            print("[" + terminal + "] ACK server=" + ack.serverInstanceId() + " seq=" + ack.currentSequence()
                    + " replayed=" + ack.replayedEvents() + (ack.resyncRequired() ? " RESYNC(" + ack.resyncReason() + ")" : ""));
        }

        @Override
        public void onResyncRequired(String reason) {
            print("[" + terminal + "] resync required: " + reason + " (a real client reloads via REST here)");
        }

        @Override
        public void onEvent(RealtimeEvent e) {
            String detail = switch (e.type()) {
                case "SLOT_OCCUPIED", "SLOT_RELEASED" -> e.payloadText("slot.slotCode") + " -> "
                        + e.payloadText("slot.status") + " (" + e.payloadText("vehicleNumber") + ")";
                case "VEHICLE_ENTERED", "VEHICLE_EXITED" -> e.payloadText("vehicleNumber") + " ticket "
                        + e.payloadText("ticketId") + " slot " + e.payloadText("parkingSlotCode");
                case "PARKING_MAP_UPDATED" -> e.payloadText("reason") + " " + e.payloadText("slot.slotCode");
                case "OPERATOR_CONNECTED", "OPERATOR_DISCONNECTED" -> e.payloadText("connection.operator") + "@"
                        + e.payloadText("connection.terminal") + " (clients: " + e.payloadText("connectedClients") + ")";
                default -> "";
            };
            print("[" + terminal + "] #" + e.sequence() + " " + e.type() + " " + detail
                    + (e.actor() == null ? "" : "  by " + e.actor()));
        }
    }

    // ----------------------------------------------------------------- helpers

    private static int failures;

    private static void check(String what, boolean ok) {
        print((ok ? "  [PASS] " : "  [FAIL] ") + what);
        if (!ok) {
            failures++;
        }
    }

    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }

    private static void summary(List<Terminal> terminals) {
        print("");
        print(String.format("%-18s %-10s %9s %10s %10s %8s", "TERMINAL", "STATE", "DELIVERED", "DUPLICATES", "RECONNECTS", "LAST SEQ"));
        for (Terminal t : terminals) {
            RealtimeClient.Stats s = t.client.stats();
            print(String.format("%-18s %-10s %9d %10d %10d %8d", t.client.terminal(), t.client.state(),
                    s.delivered(), s.duplicatesDropped(), s.reconnects(), s.lastSequence()));
        }
        print("");
    }

    private static synchronized void print(String line) {
        System.out.println(LocalTime.now().format(TIME) + "  " + line);
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) {
                opts.put(args[i].substring(2), args[++i]);
            } else {
                opts.put("mode", args[i]);
            }
        }
        return opts;
    }
}
