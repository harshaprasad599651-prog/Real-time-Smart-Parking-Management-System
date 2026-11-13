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
2. **Server:** every write endpoint checks the token and the role (`401` / `403`). A modified client still cannot do what the role does not allow.

## Screens

| Screen | Roles | Data (REST) | Live updates (WebSocket) |
|---|---|---|---|
| Login | – | `GET /api/health` (server reachability) | – |
| Overview | ADMIN | `GET /api/statistics`, `GET /api/slots` | all events; slot events update the map tile directly |
| Vehicle Entry | ENTRY_OPERATOR, ADMIN | `GET /api/categories`, `GET /api/slots`; `POST /api/parking/entry` | slot events update the map; feed of entries and exits |
| Vehicle Exit | EXIT_OPERATOR, ADMIN | `GET /api/parking/active/{vehicle}`, `GET /api/parking/sessions/active`, `GET /api/slots`; `POST /api/parking/exit` | map; parked list; details cleared if another gate checks the vehicle out |
| Terminals | ADMIN | `GET /api/realtime/status` | operators connecting/disconnecting |

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

## Exit workflow

1. Type the vehicle number (or click it in **Vehicles currently parked**) and press **Find**: `GET /api/parking/active/{vehicle}`.
2. The session card shows the slot, location, ticket, category, entry time, time parked and entry operator. The slot is **highlighted in orange** on the map.
3. **Confirm departure:** `POST /api/parking/exit` stores the exit time, completes the session and releases the slot. The banner shows the duration, and every terminal's map turns the slot green.
4. If another terminal checks the same vehicle out first, the live `VEHICLE_EXITED` event clears the card here ("already checked out at EXIT-GATE-2").

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
| `ReceiptAndValidationTest` | Receipt lines (server entry time in local zone) and vehicle-number validation |
| `ApiClientErrorParsingTest` | Problem+json, non-JSON errors and unreachable server become clear `ApiException`s |
| `EventSequenceTrackerTest` | Duplicate / gap / server-restart handling of real-time events |
| `BackendIntegrationTest` | Against a running server: all roles sign in, wrong password, token revoked → `EXPIRED`, authenticated REST + WebSocket, revoked token → WebSocket `UNAUTHORIZED` (no retry loop). Skipped if no server is running |
