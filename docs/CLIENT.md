# SmartPark JavaFX client: how it is built

One program runs on all three PCs. The account that signs in decides what the operator sees.

## Layers

```
 FXML views + controllers (ui/*)              what the operator sees
        │ use
 AppContext ── Navigator (ui/navigation)       which screen, and is this role allowed?
        │
 SessionManager   RealtimeService              who is signed in · live connection for the UI
        │               │
 ApiClient         RealtimeClient              REST (java.net.http.HttpClient) · WebSocket (java.net.http.WebSocket)
        └──── LAN ──────┘
          SmartPark server (Admin PC)
```

| Class | Responsibility |
|---|---|
| `SmartParkClientApp` | Creates the `AppContext`, sizes the window to the screen, and wires session events to navigation |
| `AppContext` | Holds the shared services and hands them to every controller (via the controller factory) |
| `ApiClient` | Async REST calls returning `CompletableFuture`, so the UI never blocks. Uses Jackson for JSON, adds `Authorization: Bearer`, turns error responses into `ApiException(status, code, message, fieldErrors)`, and reports any 401 to the `SessionManager` |
| `SessionManager` | `login()` → `POST /api/auth/login`; `logout()` → `POST /api/auth/logout`; fires `SIGNED_IN`, `SIGNED_OUT`, `EXPIRED`. Keeps the token in memory only |
| `RealtimeClient` | WebSocket with the token in the handshake: heartbeat, reconnect with back-off, replay and de-duplication. Stops with `UNAUTHORIZED` if the server refuses the token |
| `RealtimeService` | Exposes `RealtimeClient` to JavaFX: observable `state`/`detail` properties, and events delivered on the FX thread through cancellable subscriptions |
| `View` + `AccessPolicy` | The list of screens with their allowed roles, home screen per role, and sidebar entries |
| `Navigator` | Shows login or the main layout, and swaps screens **after checking `AccessPolicy`**. An unauthorised screen is never loaded; the access-denied screen is shown instead |
| `LiveView` | Base class for data screens: REST load on show, real-time subscription, debounced refresh, reload on resync, clean-up on hide |

## Sign-in flow

```
LoginController ── login(u, p) ──► SessionManager ── POST /api/auth/login ──► server
                                        │ SIGNED_IN (token, role)
SmartParkClientApp listener ◄───────────┘
   ├─ RealtimeService.start(session)        WebSocket opens with the bearer token
   └─ Navigator.showMainLayout(session)     sidebar = AccessPolicy.sidebarViews(role)
                                            screen  = AccessPolicy.homeView(role)
```

When the session ends, the app returns to the login screen with an explanation:

| Cause | Detected by | Message |
|---|---|---|
| Operator clicks **Sign out** or closes the window | `SessionManager.logout()` (token revoked on the server) | "You have signed out." |
| Token expired or revoked, server restarted | any REST call → 401, **or** WebSocket handshake → 401 | "Your session has expired or was ended by the server." |

## Role protection: two layers

1. **Client:** the sidebar only lists permitted screens, and `Navigator.navigate()` checks `AccessPolicy` again before loading anything.
2. **Server:** every endpoint except sign-in and health checks the token and the role (`401` / `403`), see the RBAC table in [API.md](API.md). A modified client still cannot do what the role does not allow.

## Screens

| Screen | Roles | Data (REST) | Live updates (WebSocket) |
|---|---|---|---|
| Login | – | `GET /api/health` (server reachability) | – |
| Overview | ADMIN | `GET /api/statistics`, `GET /api/slots` | all events; slot events update the map tile directly |
| Vehicle Entry | ENTRY_OPERATOR, ADMIN | `GET /api/categories`, `GET /api/slots`; `POST /api/parking/entry` | slot events update the map; feed of entries and exits |
| Vehicle Exit | EXIT_OPERATOR, ADMIN | `GET /api/parking/active/{vehicle}`, `GET /api/parking/sessions/active`, `GET /api/slots`; `POST /api/parking/exit` | map; parked list; details cleared if another gate checks the vehicle out |
| Parked vehicles | ADMIN | `GET /api/parking/sessions/active` | refreshed on every entry/exit |
| Parking history | ADMIN | `GET /api/parking/sessions?vehicleNumber=&status=&from=&to=` (paged) | refreshed on every entry/exit |
| Slot management | ADMIN | `GET /api/slots`, `GET /api/categories`; `POST/PUT /api/slots`, `.../disable`, `.../enable` | refreshed on slot and map events |
| Users | ADMIN | `GET/POST /api/users`, `PUT .../role`, `.../activate`, `.../deactivate` | refreshed on `ACTIVITY_RECORDED` for user changes |
| Activity | ADMIN | `GET /api/activity?role=&action=&username=` | new entries prepended live (`ACTIVITY_RECORDED`) |
| Terminals | ADMIN | `GET /api/realtime/status` | operators connecting/disconnecting |

## Admin dashboard

- **Overview:** seven KPI cards (total, available, occupied and disabled slots, vehicles parked, entries and exits today); a pie chart of slot status (green/red/grey, same colours as the map); a bar chart of entries and exits per hour today (`HourlyTraffic`, unit-tested); the live parking map, whose occupied tiles show the parked vehicle's number; and the live event feed.
- **Parked vehicles:** every ACTIVE session (vehicle, category, slot, entry time, time parked, ticket, entry operator, notes) with a quick filter. Double-click a row for the full session.
- **Parking history:** search by vehicle number (normalized like the server), entry date range (both days inclusive) and status, 25 rows per page. **View session** or a double-click opens the session details, including both operators.
- **Slot management:** add and edit slots (code, type, floor, row, bay, compatible categories or "derive from type"), and disable/enable them after a confirmation. Occupied slots cannot be edited or disabled.
- **Users:** create operator accounts, assign roles, activate/deactivate. The buttons are disabled on your own row, and the server enforces the same rules (no self-change, never remove the last admin).
- **Activity:** server-side filters by role, action and user; a bar chart of entries and exits per operator; new activity appears without refreshing.

All admin screens are **live**: they load over REST, then react to WebSocket events with a short debounce, so nothing needs a manual refresh. Forms use the non-blocking `FormDialog`; destructive actions use `ConfirmDialog`.

## Entry Operator dashboard

```
┌ Total slots ┐┌ Available ┐┌ Occupied ┐┌ Current vehicle ┐      KPI strip (live)
┌ Register incoming vehicle ──────┐ ┌ Parking map ───────────────────────────┐
│ 1. Vehicle number               │ │ legend: fits this vehicle · other      │
│ 2. Category  ──────────────────────► GET /api/slots/available?category=   │
│    Notes (optional, 200 chars)  │ │ FLOOR 0  A [A-01][A-02]...             │
│ 3. Selected slot + vehicle      │◄─ click a highlighted tile              │
│ [Clear] [Confirm allocation] ─► confirmation dialog ─► POST /api/parking/entry
│ LAST TICKET ISSUED  SP-...      │ │                                        │
└─────────────────────────────────┘ └────────────────────────────────────────┘
  Gate activity (live feed)
```

| Step | What happens |
|---|---|
| Vehicle number | Upper-cased as typed, checked with the server's rules (`VehicleNumbers`) |
| Category | `ParkingService.availableSlots(category)` asks the server for the available slots. `SlotMapModel.syncAvailability` applies them; if the local map disagrees, the whole map is reloaded from the server |
| Map colours | **Green** = available (solid green with a ring = fits the selected vehicle; light green = for other vehicle types), **red** = occupied, **grey** dashed = disabled, **blue** = selected. Only highlighted tiles can be clicked |
| Select slot | The summary shows the slot code and location plus the vehicle number and category; the *Current vehicle* KPI updates as you type |
| Confirm allocation | `ConfirmDialog` (reusable component) lists slot, location, vehicle, category, terminal and notes. If a live event takes the slot while the dialog is open, the request is not sent |
| Server decision | `POST /api/parking/entry` (via `ParkingService.registerEntry`). **The server is the only authority:** 201 shows the ticket ID in *Last ticket issued*, marks the slot occupied and prepares the receipt; 409 `SLOT_UNAVAILABLE` tells the operator and reloads the map |
| Receipt | `ReceiptDialog` shows the prepared receipt (ticket, vehicle, category, slot, location, server entry date and time, operator, terminal). Physical printing is a later stage; `ReceiptView` is a self-contained node ready for `PrinterJob` |
| Real time | `SLOT_RELEASED` from an exit gate turns the tile green immediately and updates the KPIs, with no reload. Allocation is disabled while the live connection is down |

*Race detail:* the server broadcasts `SLOT_OCCUPIED` before the HTTP response reaches the entry terminal. While its own request is in flight, the entry screen therefore ignores "your selected slot was taken" notifications. A genuine conflict always comes back as a 409.

**Separation:** `VehicleEntryController` handles the screen only. `ParkingService` does the REST calls and event parsing, `SlotMapModel` holds the map rules (plain Java, unit-tested), and `SlotMapView`, `StatCard`, `ConfirmDialog` and `ActivityFeed` are reusable components.

## Exit Operator dashboard

| Step | What happens |
|---|---|
| Search | Type the vehicle number (or click it in *Vehicles currently parked*) and press **Find**: `ParkingService.findActiveSession` → `GET /api/parking/active/{vehicle}` |
| Session card | Vehicle, category, ticket ID, assigned slot and location, entry time, time parked, entry operator, notes, and a **status badge** (`ACTIVE · parked 52 min`). The slot is highlighted orange on the live map |
| Confirm vehicle exit | `ConfirmDialog` shows vehicle, slot, ticket, entry time and time parked. On OK: `POST /api/parking/exit` |
| Server (transactional) | Completes the session and releases the slot as one unit of work: a MongoDB transaction on a replica set, otherwise atomic conditional updates. It broadcasts `SLOT_RELEASED` + `VEHICLE_EXITED` **only after** it has committed |
| Success | The *Exit completed* card shows vehicle, slot released, ticket and `COMPLETED`, entry and exit times, the exit operator and the duration. Only now does this screen show the slot as free; every entry terminal and the admin dashboard update at the same moment via the WebSocket |

### Error handling (`ExitMessages`, unit-tested)

| Situation | Behaviour |
|---|---|
| Invalid vehicle number | Rejected locally, no request: "Use 2-12 letters and digits, e.g. WP CAB-1234." |
| No active session, never parked | "ZZZ999 has no active parking session. Check the number on the ticket." |
| Already completed session | The client asks for the vehicle's last completed session: "WPCAB1234 is not parked now: ticket SP-… was already completed on 13 Nov, 13:24 by exit1 (slot A-03)." |
| Checked out at another terminal meanwhile | A live `VEHICLE_EXITED` event clears the card with an explanation. If the exit is confirmed anyway, the server answers 404 `NO_ACTIVE_SESSION` → "already checked out… Nothing was changed." |
| Network disconnection | **Confirm vehicle exit** is disabled while the live connection is down, with a hint; a search shows "Cannot reach the SmartPark server…" |
| Connection lost / 5xx **after** the exit was sent | The outcome is unknown, so the client asks the server (`GET /api/parking/sessions/{ticket}`): COMPLETED → success card; ACTIVE → "The exit was NOT recorded… confirm again"; still unreachable → "unknown whether the exit was recorded. Do not let the vehicle leave yet." The operator is never told *done* unless the server says so |
| Other server errors | Shown with the server's error code; nothing is assumed to have changed |

## Parking map (`SlotMapView` + `SlotMapModel`)

* `SlotMapModel` is plain Java (unit-tested): slots, the category filter, selection, highlight, and tile state (`FREE`, `INCOMPATIBLE`, `OCCUPIED`, `DISABLED`, `SELECTED`, `HIGHLIGHTED`).
* `SlotMapView` draws one section per floor and one line per row, with tiles in column order, and redraws whenever the model changes. It is used in selectable mode (entry) and read-only mode (exit, admin).
* Updates: a REST `GET /api/slots` on show or resync; each `SLOT_OCCUPIED`, `SLOT_RELEASED` or `PARKING_MAP_UPDATED` event carries the changed slot and is applied directly (no reload). A category change (`slot = null`) triggers a reload.

## Look and feel

* All styling is in [`smartpark.css`](../smartpark-client/src/main/resources/css/smartpark.css): colour tokens (navy sidebar, blue primary, green/amber/red status), a Segoe UI type scale, cards, KPI tiles, tables, badges.
* Icons are vector `SVGPath`s (Material Design paths), coloured by CSS; there are no image files.
* Layouts adapt to the window: the window opens at 90 % of the screen (min 1024×640), KPI tiles wrap (`FlowPane`), two-column areas use percentage columns, and content scrolls vertically.

## Tests

| Test | What it proves |
|---|---|
| `AccessPolicyTest` | Each role's home screen and sidebar; operators cannot open other roles' screens |
| `SlotMapModelTest` | Layout order, compatible-free-only selection, toggle, category change, live event taking the selected slot, release, server availability is authoritative, highlight |
| `ExitMessagesTest` | Never parked vs already completed (when, by whom), which errors make an exit outcome unknown, search and rejection messages |
| `ReceiptAndValidationTest` | Receipt lines (server entry time in local zone) and vehicle-number validation |
| `ApiClientErrorParsingTest` | Problem+json, non-JSON errors and unreachable server become clear `ApiException`s |
| `EventSequenceTrackerTest` | Duplicate / gap / server-restart handling of real-time events |
| `BackendIntegrationTest` | Against a running server: all roles sign in, wrong password, token revoked → `EXPIRED`, authenticated REST + WebSocket, revoked token → WebSocket `UNAUTHORIZED` (no retry loop). Skipped if no server is running |
