# SmartPark real-time communication (WebSocket)

## 1. REST vs WebSocket: why SmartPark uses both

| | REST (HTTP) | WebSocket |
|---|---|---|
| **Connection** | A new request/response for every call. The connection may be reused, but each exchange is independent. | One long-lived TCP connection, opened by an HTTP `Upgrade` handshake and then kept open. |
| **Who starts the conversation** | Always the **client**. The server can only answer. | **Either side**, at any time. The server can *push*. |
| **Typical use** | Commands and queries: "park this car", "give me the slot list". | Notifications: "slot A-01 was just taken". |
| **Result** | Each request gets its own status code (201, 409 ...), so a client knows whether its own command worked. | Fire-and-forget messages to everyone; no per-message reply. |
| **Keeping a screen up to date** | The client would have to **poll** ("anything new?") every few seconds. That is wasteful and still slow. | The update arrives within milliseconds of the change. |
| **State** | Stateless; every request is self-contained. | Stateful: the server remembers who is connected. |

**How SmartPark combines them:**

```
 Entry PC                          Server (Admin PC)                         Exit PC / Admin PC
    │  REST POST /api/parking/entry     │                                          │
    │──────────────────────────────────►│  atomic slot claim in MongoDB            │
    │◄────────── 201 + ticket ──────────│                                          │
    │                                   │  ParkingDomainEvent ─► RealtimeHub        │
    │◄════ WS SLOT_OCCUPIED #41 ════════│════════ WS SLOT_OCCUPIED #41 ═══════════►│
    │◄════ WS VEHICLE_ENTERED #42 ══════│════════ WS VEHICLE_ENTERED #42 ═════════►│
```

1. **Every change is a REST command.** The operator who parks a car gets a definite answer (201 with the receipt, or 409 "slot taken"). Validation, roles and concurrency control all happen there.
2. **Every committed change is then broadcast over the WebSocket** to *all* connected clients, including the one that made it. Every map stays identical without polling.
3. **Initial load and recovery use REST.** When a client starts, or has missed too much, it reloads the slot map and active sessions with `GET` requests. After that, WebSocket events keep it current.

Clients never send commands over the WebSocket. The only client-to-server traffic there is heartbeats. Keeping all writes in REST means there is exactly one place where rules and locking are enforced.

---

## 2. Endpoint and handshake

```
ws://<server-ip>:8080/ws?operator=entry1&terminal=ENTRY-GATE-1&clientId=<uuid>[&since=<seq>&instance=<id>]
```

The operator can also be sent in the `X-Operator` header (the Java client does this).

| Parameter | Meaning |
|---|---|
| `operator` / `X-Operator` | **Required.** An existing, active user. Otherwise the handshake is refused with **HTTP 403** and the socket never opens. |
| `terminal` | Name shown on the admin dashboard, e.g. `ENTRY-GATE-1` |
| `clientId` | Stable per running client. If a client reconnects while its old connection still looks open (half-open TCP), the server closes the old one (code 1008), so an operator is never counted twice. |
| `since`, `instance` | Sent on **re**connect: the last sequence processed and the server it came from. The server replays the missed events. |

Live connections: `GET /api/realtime/status` lists operator, role, terminal, IP:port, connected-since and last heartbeat.

---

## 3. Message format

Every message is one JSON text frame:

```json
{
  "eventId": "8a6e1c1e-...",
  "sequence": 41,
  "serverInstanceId": "af1cee4c",
  "type": "SLOT_OCCUPIED",
  "timestamp": "2026-11-13T08:15:30.123Z",
  "actor": "entry1",
  "terminal": "ENTRY-GATE-1",
  "payload": { "slot": { "slotCode": "A-01", "status": "OCCUPIED", "...": "..." },
               "reason": "VEHICLE_ENTRY", "ticketId": "SP-20261113-000001", "vehicleNumber": "WPCAB1234" }
}
```

| Type | Sent when | Payload |
|---|---|---|
| `VEHICLE_ENTERED` | Entry succeeded | the ACTIVE session (ticket, vehicle, category, slot, entry time) |
| `VEHICLE_EXITED` | Exit confirmed | the COMPLETED session (exit time, duration) |
| `SLOT_OCCUPIED` | Slot assigned (sent just before `VEHICLE_ENTERED`) | `slot`, `reason`, `ticketId`, `vehicleNumber` |
| `SLOT_RELEASED` | Slot freed by an exit (just before `VEHICLE_EXITED`) or by the reconciliation job | `slot`, `reason` (`VEHICLE_EXIT` / `RECONCILIATION`) |
| `PARKING_MAP_UPDATED` | Slot created, edited, disabled or enabled; category created or updated | `reason`, `slot` (**null = reload the whole map**), `categoryName` |
| `OPERATOR_CONNECTED` | A client connected | `connection` {operator, role, terminal, remoteAddress, connectedAt}, `connectedClients` |
| `OPERATOR_DISCONNECTED` | A client closed, lost its network, or failed the heartbeat | same, plus `closeReason` |
| `CONNECTION_ACK` *(control, sequence 0)* | First message on every connection | `serverInstanceId`, `currentSequence`, `replayedEvents`, `resyncRequired`, `resyncReason`, `heartbeatSeconds`, `connectedClients` |
| `PONG` *(control)* | Reply to a text `PING` | – |

Rejected operations (409, 422 ...) broadcast **nothing**. Events are only published after MongoDB has stored the change, and only once, even if a transaction was retried.

---

## 4. Reliability

### Ordering and duplicate prevention
* **Server:** every broadcast event gets the next `sequence` (1, 2, 3 ...) under a lock and is queued to all clients in the same step. Each client has its own outgoing queue with a single sender thread. So every client receives every event **in the same order**, and one slow client never delays the others. A client more than 1000 messages behind is disconnected; it reconnects and catches up.
* **Client** ([EventSequenceTracker](../smartpark-client/src/main/java/com/smartpark/client/realtime/EventSequenceTracker.java)): drops any event whose `sequence` is ≤ the last processed one, or whose `eventId` was already seen. A jump in `sequence` triggers a resync.

### Connection monitoring
* **Client → server:** a WebSocket **ping every 15 s**. If nothing at all arrives for 45 s, the client declares the link dead and reconnects, even if Windows hasn't noticed the drop yet.
* **Server → client:** a ping every 20 s. A client silent for 60 s is closed and announced as `OPERATOR_DISCONNECTED`. Tomcat also drops sessions idle for 2 minutes.

### Disconnection and reconnection
1. The link drops (Wi-Fi lost, cable pulled, server restarted).
2. The client goes to `RECONNECTING` (the UI should show a red indicator and block parking actions) and retries after about 1, 2, 4 ... 30 s, with random jitter.
3. It reconnects with `since=<last sequence>&instance=<server id>`.
4. The server answers `CONNECTION_ACK` and then:
   * same server, missed events still in its 1000-event history: **replays them in order** (`replayedEvents = n`);
   * server restarted, or too many missed events: `resyncRequired = true`, and the client reloads its state with REST `GET`s.

---

## 5. Java test client

The library [RealtimeClient](../smartpark-client/src/main/java/com/smartpark/client/realtime/RealtimeClient.java) uses only the JDK's `java.net.http.WebSocket`. No browser or JavaScript is involved. The command-line tool [RealtimeTestClient](../smartpark-client/src/main/java/com/smartpark/client/tools/RealtimeTestClient.java) uses it in two modes. Start the server first.

### Automatic demo (one PC)

```powershell
.\mvnw.cmd -pl smartpark-client compile exec:java "-Dexec.args=demo --clients 5"
```

This connects 5 terminals (`ADMIN-DASHBOARD`, `ENTRY-GATE-1`, `EXIT-GATE-1`, `MONITOR-1`, `MONITOR-2`), parks a car through REST, **cuts the exit terminal's connection**, releases the car while that terminal is offline, and checks that everyone received everything exactly once:

```
>>> REST POST /api/parking/entry  DEMO6253 -> A-01  (as entry1)
[ADMIN-DASHBOARD] #41 SLOT_OCCUPIED A-01 -> OCCUPIED (DEMO6253)  by entry1
[EXIT-GATE-1]     #42 VEHICLE_ENTERED DEMO6253 ticket SP-20261113-000001 slot A-01  by entry1
  [PASS] every client received SLOT_OCCUPIED + VEHICLE_ENTERED for DEMO6253
>>> Simulating network failure on EXIT-GATE-1
[EXIT-GATE-1] state RECONNECTING - simulated network failure - retry in 903 ms
>>> REST POST /api/parking/exit   DEMO6253  (as exit1)
[EXIT-GATE-1] state CONNECTED - localhost:8080
[EXIT-GATE-1] ACK server=af1cee4c seq=45 replayed=3
[EXIT-GATE-1] #44 SLOT_RELEASED A-01 -> AVAILABLE (DEMO6253)  by exit1        <- replayed
[EXIT-GATE-1] #45 VEHICLE_EXITED DEMO6253 ticket SP-20261113-000001 ...       <- replayed
  [PASS] every client received SLOT_RELEASED + VEHICLE_EXITED for DEMO6253
  [PASS] no client processed any event twice
RESULT: PASS
```

> The demo really parks and releases a vehicle named `DEMO####`, which appears in the parking history.

### Live monitor (run on all three PCs)

```powershell
# on the Entry PC
.\mvnw.cmd -pl smartpark-client compile exec:java "-Dexec.args=listen --host 192.168.1.10 --operator entry1 --terminal ENTRY-GATE-1"
# on the Exit PC
.\mvnw.cmd -pl smartpark-client compile exec:java "-Dexec.args=listen --host 192.168.1.10 --operator exit1 --terminal EXIT-GATE-1"
```

Then park or release a vehicle (Swagger UI or `smartpark-api.http`) and watch the event appear on every PC at once. Pull a network cable to see `RECONNECTING`, and the replay when it comes back. Press **Enter** to quit.

### Automated tests
* Server: [RealtimeWebSocketTest](../smartpark-server/src/test/java/com/smartpark/server/websocket/RealtimeWebSocketTest.java). Real WebSocket clients against the running server cover: ACK, refused handshake, broadcast to 3 clients, nothing broadcast on a rejected entry, map updates, presence, connection replacement, replay without gaps, resync after a server restart, and PING/PONG.
* Client: [EventSequenceTrackerTest](../smartpark-client/src/test/java/com/smartpark/client/realtime/EventSequenceTrackerTest.java) covers duplicate, gap and restart handling.
