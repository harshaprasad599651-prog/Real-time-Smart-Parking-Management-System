# SmartPark – Testing results

Final test campaign, 13 November 2026. Everything below was run against the final code.

| Level | What | Result |
|---|---|---|
| Automated unit + integration tests | `mvnw clean verify`: 172 server tests, 35 client tests | **207 / 207 pass** |
| REST system test | Every one of the 31 API operations, 61 checks (success and refusal cases) | **61 / 61 pass** |
| WebSocket, multiple clients | 5 simultaneous clients, network cut and replay | **PASS** |
| Simultaneous allocation (live server) | 20 concurrent requests for one slot; one vehicle into 10 slots at once | **1 winner each, no inconsistency** |
| Vehicle entry and exit (GUI, three clients) | Entry assigns A-01 → admin map → exit → entry map | **PASS** |
| Receipt generation and printing | Preview, print through the Windows print system, reprint by ticket ID | **PASS** |
| LAN connectivity | Release packages; all clients connect through the Wi-Fi LAN address | **PASS** (single PC, see §6) |
| Server disconnection and restart | Stop and restart the server with three clients connected | **PASS**, automatic recovery |
| Database outage | MongoDB stopped and restarted under a running server | **PASS** (503 within 5 s, recovery within 1 s) |

---

## 1. Automated tests

Run with `mvnw.cmd clean verify` (MongoDB must be running locally; tests use the separate database `smartpark_test_db`).

### Server (172 tests)

| Area | Test class | Tests | What it proves |
|---|---|---|---|
| Persistence | `MongoConnectivityTest` | 4 | Connection, database name, transaction support detection |
| | `MongoIndexTest` | 6 | All unique, partial-unique and TTL indexes exist |
| | `DocumentPersistenceTest` | 5 | Every document type is saved and read back correctly |
| | `DataSeederTest` | 3 | Sample data only into empty collections; slot categories derived from slot types |
| Business rules | `ParkingSessionServiceTest` | 11 | Entry, exit, ticket IDs, compatibility, reconciliation of orphaned slots |
| | `DuplicatePreventionTest` | 12 | Duplicate usernames, categories, slots, vehicles and slot claims are refused, also directly at the index |
| Concurrency | `ConcurrentSlotAllocationTest` | 8 | 20 operators for one slot → 1 winner; one vehicle racing for many slots → 1; many vehicles for few slots → no double booking; concurrent exits → 1 |
| | `ParkingConsistencyTest` | 11 | 12 terminals × 30 random entries/exits (3 runs): slots and sessions agree exactly afterwards; disable-vs-allocate race has one winner; re-entry; refused roles and deactivated accounts |
| REST API | `ParkingApiTest` | 13 | Entry/exit endpoints, normalisation, notes, errors |
| | `ConcurrentEntryApiTest` | 2 | Simultaneous HTTP entries for one slot / exits for one vehicle |
| | `ParkingSlotApiTest`, `VehicleCategoryApiTest` | 10 + 9 | Slot and category management, validation, conflicts |
| | `MonitoringApiTest` | 4 | Active sessions, history paging and filters, statistics |
| | `ReceiptApiTest` | 6 | Ticket stored with the entry, no receipt for a rejected entry, reprints never create sessions |
| | `UserApiTest` | 7 | Account creation, roles, activation, last-admin and self-lockout protection |
| | `ActivityApiTest` | 2 | Activity filters; live activity only to admins |
| Security | `AuthApiTest`, `OperatorHeaderDisabledTest` | 7 + 2 | Sign-in, tokens, expiry, logout; password-less header mode is off by default |
| | `AccessControlApiTest` | 6 | Every role can do exactly what it should (server-side RBAC) |
| | `SecurityHardeningApiTest` | 8 | Brute-force lockout (429), equal answers for unknown users, password policy, log-injection and notes sanitisation, audit of refused operations |
| | `AuthTokenPersistenceTest`, `LoginAttemptLimiterTest` | 4 + 4 | Sign-ins survive restarts and are stored as hashes; limiter windows and lockouts |
| | `GlobalExceptionHandlerTest`, `NormalizerTest` | 4 + 6 | 503/409/429/500 mappings without internals; input sanitising |
| Real-time | `RealtimeWebSocketTest` | 10 | ACK, refused handshake, broadcast to 3 clients, nothing broadcast for rejected entries, presence, replay without gaps, resync after restart, ping/pong |
| | `RealtimeSecurityTest` | 3 | Connections cannot be hijacked; deactivation closes live connections; terminal names sanitised |
| Other | `HealthControllerTest`, `LanAddressReporterTest` | 2 + 3 | Database-aware health check; LAN address detection |

### Client (35 tests)

| Test class | Tests | What it proves |
|---|---|---|
| `SlotMapModelTest` | 8 | Map layout, only compatible free slots selectable, live events taking the selected slot, server is authoritative |
| `ExitMessagesTest` | 5 | Never-parked vs already-exited messages; which errors leave the outcome unknown |
| `ReceiptAndValidationTest` | 5 | Receipt lines from the server time, reprint data, template customisation, paper-size detection, vehicle-number validation |
| `EventSequenceTrackerTest` | 5 | Duplicate, gap and server-restart detection for real-time events |
| `AccessPolicyTest` | 4 | Each role's screens and sidebar |
| `ApiClientErrorParsingTest` | 4 | Server errors and network failures become clear messages |
| `ClientConfigTest` | 3 | Server address from file / JVM options |
| `HourlyTrafficTest` | 1 | Hourly entry/exit chart data |
| `BackendIntegrationTest` | – | Runs only when a server is running (sign-in, token expiry, REST + WebSocket); skipped in the build, passed in earlier stages |

---

## 2. REST API system test (61 checks)

Script: [`tests/system/endpoint-test.ps1`](../tests/system/endpoint-test.ps1), against the server started from the release package on a fresh database. It calls **all 31 operations** published in the OpenAPI specification, with the correct role and with the important refusals.

| # | Operation | Case | Expected | Actual | Result |
|---|---|---|---|---|---|
| E01 | `GET /api/health` | public, database up | 200 | 200 | PASS |
| E02 | `POST /api/auth/login` | valid credentials -> token | 200 | 200 | PASS |
| E03 | `POST /api/auth/login` | wrong password | 401 INVALID_CREDENTIALS | 401 INVALID_CREDENTIALS | PASS |
| E04 | `POST /api/auth/login` | blank fields | 400 VALIDATION_FAILED | 400 VALIDATION_FAILED | PASS |
| E05 | `GET /api/auth/me` | entry operator | 200 | 200 | PASS |
| E06 | `GET /api/auth/me` | no token | 401 UNAUTHENTICATED | 401 UNAUTHENTICATED | PASS |
| E07 | `GET /api/categories` | signed in | 200 | 200 | PASS |
| E08 | `GET /api/categories/{name}` | case-insensitive | 200 | 200 | PASS |
| E09 | `GET /api/categories/{name}` | unknown | 404 NOT_FOUND | 404 NOT_FOUND | PASS |
| E10 | `POST /api/categories` | admin creates | 201 | 201 | PASS |
| E11 | `POST /api/categories` | entry operator | 403 OPERATOR_NOT_AUTHORIZED | 403 OPERATOR_NOT_AUTHORIZED | PASS |
| E12 | `POST /api/categories` | duplicate | 409 DUPLICATE_RESOURCE | 409 DUPLICATE_RESOURCE | PASS |
| E13 | `PUT /api/categories/{name}` | admin updates | 200 | 200 | PASS |
| E14 | `GET /api/slots` | map order | 200 | 200 | PASS |
| E15 | `GET /api/slots` | no token | 401 UNAUTHENTICATED | 401 UNAUTHENTICATED | PASS |
| E16 | `GET /api/slots/available` | for CAR | 200 | 200 | PASS |
| E17 | `GET /api/slots/{code}` | case-insensitive | 200 | 200 | PASS |
| E18 | `POST /api/slots` | admin adds (categories derived) | 201 | 201 | PASS |
| E19 | `POST /api/slots` | invalid code | 400 VALIDATION_FAILED | 400 VALIDATION_FAILED | PASS |
| E20 | `PUT /api/slots/{code}` | admin edits | 200 | 200 | PASS |
| E21 | `POST /api/slots/{code}/disable` | available -> disabled | 200 | 200 | PASS |
| E22 | `POST /api/slots/{code}/enable` | disabled -> available | 200 | 200 | PASS |
| E23 | `POST /api/slots/{code}/disable` | exit operator | 403 OPERATOR_NOT_AUTHORIZED | 403 OPERATOR_NOT_AUTHORIZED | PASS |
| E24 | `POST /api/parking/entry` | valid entry | 201 | 201 | PASS |
| E25 | `POST /api/parking/entry` | slot occupied | 409 SLOT_UNAVAILABLE | 409 SLOT_UNAVAILABLE | PASS |
| E26 | `POST /api/parking/entry` | vehicle already parked | 409 VEHICLE_ALREADY_PARKED | 409 VEHICLE_ALREADY_PARKED | PASS |
| E27 | `POST /api/parking/entry` | incompatible slot | 422 SLOT_NOT_COMPATIBLE | 422 SLOT_NOT_COMPATIBLE | PASS |
| E28 | `POST /api/parking/entry` | invalid vehicle number | 400 VALIDATION_FAILED | 400 VALIDATION_FAILED | PASS |
| E29 | `POST /api/parking/entry` | exit operator | 403 OPERATOR_NOT_AUTHORIZED | 403 OPERATOR_NOT_AUTHORIZED | PASS |
| E30 | `GET /api/parking/receipts/{ticket}` | stored ticket | 200 | 200 | PASS |
| E31 | `POST /api/parking/receipts/{ticket}/prints` | first print | 200 | 200 | PASS |
| E32 | `POST /api/parking/receipts/{ticket}/prints` | reprint (count 2) | 200 | 200 | PASS |
| E33 | `GET /api/parking/receipts/{ticket}` | unknown ticket | 404 NOT_FOUND | 404 NOT_FOUND | PASS |
| E34 | `GET /api/parking/receipts/{ticket}` | exit operator | 403 OPERATOR_NOT_AUTHORIZED | 403 OPERATOR_NOT_AUTHORIZED | PASS |
| E35 | `GET /api/parking/sessions/active` | one parked | 200 | 200 | PASS |
| E36 | `GET /api/parking/sessions/{ticket}` | by ticket | 200 | 200 | PASS |
| E37 | `GET /api/statistics` | occupancy | 200 | 200 | PASS |
| E38 | `GET /api/parking/active/{vehicle}` | found, normalized | 200 | 200 | PASS |
| E39 | `GET /api/parking/active/{vehicle}` | not parked | 404 NO_ACTIVE_SESSION | 404 NO_ACTIVE_SESSION | PASS |
| E40 | `GET /api/parking/active/{vehicle}` | entry operator | 403 OPERATOR_NOT_AUTHORIZED | 403 OPERATOR_NOT_AUTHORIZED | PASS |
| E41 | `POST /api/parking/exit` | entry operator | 403 OPERATOR_NOT_AUTHORIZED | 403 OPERATOR_NOT_AUTHORIZED | PASS |
| E42 | `POST /api/parking/exit` | valid exit | 200 | 200 | PASS |
| E43 | `POST /api/parking/exit` | second exit | 404 NO_ACTIVE_SESSION | 404 NO_ACTIVE_SESSION | PASS |
| E44 | `GET /api/slots/{code}` | slot free after exit | 200 | 200 | PASS |
| E45 | `GET /api/parking/sessions` | history search | 200 | 200 | PASS |
| E46 | `GET /api/parking/sessions` | page size too large | 400 VALIDATION_FAILED | 400 VALIDATION_FAILED | PASS |
| E47 | `GET /api/parking/sessions` | entry operator | 403 OPERATOR_NOT_AUTHORIZED | 403 OPERATOR_NOT_AUTHORIZED | PASS |
| E48 | `GET /api/users` | admin, no hashes | 200 | 200 | PASS |
| E49 | `POST /api/users` | create operator | 201 | 201 | PASS |
| E50 | `POST /api/users` | weak password | 400 VALIDATION_FAILED | 400 VALIDATION_FAILED | PASS |
| E51 | `PUT /api/users/{user}/role` | assign role | 200 | 200 | PASS |
| E52 | `POST /api/users/{user}/deactivate` | deactivate | 200 | 200 | PASS |
| E53 | `POST /api/users/{user}/activate` | activate | 200 | 200 | PASS |
| E54 | `POST /api/users/{user}/deactivate` | own account | 409 INVALID_STATE | 409 INVALID_STATE | PASS |
| E55 | `GET /api/users` | entry operator | 403 OPERATOR_NOT_AUTHORIZED | 403 OPERATOR_NOT_AUTHORIZED | PASS |
| E56 | `GET /api/activity` | filter by action | 200 | 200 | PASS |
| E57 | `GET /api/activity` | exit operator | 403 OPERATOR_NOT_AUTHORIZED | 403 OPERATOR_NOT_AUTHORIZED | PASS |
| E58 | `GET /api/realtime/status` | admin | 200 | 200 | PASS |
| E59 | `GET /api/realtime/status` | entry operator | 403 OPERATOR_NOT_AUTHORIZED | 403 OPERATOR_NOT_AUTHORIZED | PASS |
| E60 | `POST /api/auth/logout` | sign out | 204 | 204 | PASS |
| E61 | `GET /api/auth/me` | token revoked after logout | 401 UNAUTHENTICATED | 401 UNAUTHENTICATED | PASS |

Besides the status code, successful calls were checked for content, for example: the ticket ID format `SP-yyyyMMdd-nnnnnn`, vehicle-number normalisation (`wp cab-1234` → `WPCAB1234`), the slot free again after the exit, print counters 1 and 2, and no password hashes in `/api/users`.

---

## 3. WebSocket communication with multiple clients

Tool: `RealtimeTestClient` (`mvnw -pl smartpark-client compile exec:java "-Dexec.args=demo --clients 5"`).

```
[PASS] all 5 clients connected
>>> REST POST /api/parking/entry  DEMO8308 -> A-01  (as entry1)
[PASS] entry accepted (ticket SP-20261113-000004)
[PASS] every client received SLOT_OCCUPIED + VEHICLE_ENTERED for DEMO8308
>>> Simulating network failure on EXIT-GATE-1
>>> REST POST /api/parking/exit   DEMO8308  (as exit1)
[PASS] exit accepted (0 min)
[EXIT-GATE-1] ACK server=b9b2034e seq=55 replayed=3
[PASS] EXIT-GATE-1 reconnected
[PASS] every client received SLOT_RELEASED + VEHICLE_EXITED for DEMO8308
[PASS] no client processed any event twice
RESULT: PASS
```

The disconnected terminal missed the exit, and on reconnecting it received the 3 missed events in order, exactly once.

## 4. Simultaneous parking allocation (live server, real HTTP)

| Test | Requests | Result |
|---|---|---|
| 20 different vehicles request slot **A-05** at the same moment | 20 parallel `POST /api/parking/entry` | **1 × 201**, 19 × 409 `SLOT_UNAVAILABLE`; A-05 holds the winner's ticket; exactly 1 active session on A-05 |
| Vehicle **TWIN-777** requested for 10 different slots at the same moment | 10 parallel requests | **1 × 201**, 9 × 409 `VEHICLE_ALREADY_PARKED`; occupied slots = active sessions (2 = 2), so no slot was left half-claimed |

## 5. Entry, exit, receipt (GUI, three clients)

The three clients were started from the release package `SmartPark-Client`, one folder per "computer", all with `smartpark.server.host=10.238.6.211` (the PC's Wi-Fi address):

| Step | Observed |
|---|---|
| Entry operator: `WP CAB-1234`, *Car*, slot **A-01**, confirm | Ticket `SP-20261113-000026`, receipt preview opens |
| Print the receipt (Microsoft Print to PDF) | PDF contains: SmartPark, ticket ID, vehicle `WPCAB1234`, category CAR, slot A-01 (Floor 0 · Row A · Bay 1), entry date and time, operator `entry1`; the server counted the print |
| Admin overview | A-01 red with `WPCAB1234` on the tile and the entry in the live activity, without refreshing |
| Exit operator: search `wp cab-1234` → confirm exit | "Exit completed – slot A-01 is free" |
| Entry operator's and admin's maps | A-01 green again, *31 free · 0 occupied* |
| Reprint: ticket ID typed in lower case | "Reprint of ticket … (printed once before)", receipt marked *DUPLICATE – REPRINT #1*; no new session |

The receipt layout for 80 mm and 58 mm thermal rolls was verified by printing both sizes to PDF in the receipt stage (true physical widths, all fields present). A receipt cancelled in the print dialog gave the error "The printer … did not accept the receipt", and no print was counted.

## 6. LAN connectivity

* The server, started with `start-server.cmd` from the release package, listens on `0.0.0.0:8080` and printed the address to use: `LAN clients: smartpark.server.host=10.238.6.211`. It skipped the VirtualBox adapter.
* `Test-Connection.cmd` from the client package: `OK: server UP, database UP`.
* `/api/realtime/status` listed all three clients connecting from `10.238.6.211`, not `127.0.0.1`.

**Limitation of this test:** only one physical PC was available, so the three "computers" were three client copies on one machine that talk to the server through its Wi-Fi LAN address. Traffic between separate machines and the Windows Firewall rule (`open-firewall.cmd`) must be confirmed on the three demonstration PCs with `Test-Connection.cmd` ([DEMO.md](DEMO.md), step 0).

## 7. Server disconnection and reconnection

| Event | Admin | Entry | Exit |
|---|---|---|---|
| Server process stopped | *Reconnecting…* | *Reconnecting…* | *Reconnecting…* (allocation paused) |
| Server started again | *Live* after ~24 s | *Live* after ~22 s | *Live* after ~23 s |

Most of the delay is server start-up time. No operator had to sign in again (sign-ins are stored in MongoDB), and every screen reloaded its data. In the LAN deployment test, an entry made while the clients were still reconnecting appeared on all of them afterwards.

**Database outage** (security stage): with MongoDB stopped, requests answered **503 DATABASE_UNAVAILABLE in 5 s** and `/api/health` reported `DEGRADED / database DOWN`. The service resumed about 1 s after MongoDB returned, without restarting the server.

## 8. Defects found and fixed

| Found in | Defect | Fix |
|---|---|---|
| Final testing | Pressing Enter in the Exit screen's search field did nothing (the operator had to click *Find*) | Enter now searches, like the other search fields |
| Final testing | The admin *Activity* filter did not offer the actions added later (`RECEIPT_PRINTED`, `RECEIPT_REPRINTED`, `LOGIN_LOCKED`, `ACCESS_DENIED`); they were only visible under *All actions* | Filter list completed (now identical to the server's 21 actions); lockouts and refusals shown with red badges |
| Security review | Any signed-in operator could disconnect another operator's live connection by reusing its client id | Only the same operator can replace a connection |
| Security review | Deactivated accounts kept their open WebSocket connection | Revoking sign-ins closes live connections |
| Security review | Sign-in response time revealed which usernames exist | Constant-time sign-in |
| Security review | Unanswered entry requests were reported as "nothing was saved" | The Entry screen verifies the outcome with the server |
| Security review | WebSocket query parameters were not URL-decoded | Decoded |
| LAN deployment | A server restart signed every terminal out | Sign-ins stored (as hashes) in MongoDB |
| Receipt stage | Enter in the paper list started printing; a second print could start before the first was recorded | Fixed; one click = one receipt |
| Admin stage | Table columns were cut off on narrow windows | Columns shrink, badges keep their width |
