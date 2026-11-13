# SmartPark REST API

Base URL: `http://<admin-pc-ip>:8080`. All bodies are JSON. All times are ISO-8601 UTC instants taken from the **server clock**, for example `2026-11-13T08:15:30.123Z`.

Interactive documentation is generated from the code while the server runs:

- **Swagger UI:** `http://<server>:8080/swagger-ui.html` (try every endpoint from the browser)
- **OpenAPI 3 spec:** `http://<server>:8080/v3/api-docs`

Ready-made requests: [`smartpark-server/http/smartpark-api.http`](../smartpark-server/http/smartpark-api.http) (VS Code *REST Client* extension or IntelliJ HTTP Client).

---

## Authentication

1. **Sign in:** `POST /api/auth/login`

   ```jsonc
   // request
   { "username": "entry1", "password": "Entry@123", "terminal": "ENTRY-GATE-1" }
   // 200 response
   { "token": "q3J9...Xw", "tokenType": "Bearer", "expiresAt": "2026-11-13T20:15:00Z",
     "user": { "username": "entry1", "role": "ENTRY_OPERATOR", "active": true, "...": "..." } }
   ```

   A wrong password, an unknown user and an inactive account all give the same **401** `INVALID_CREDENTIALS`.
2. **Use the token:** send `Authorization: Bearer <token>` with every write request and with the WebSocket handshake. Each use extends it; it expires after 12 hours unused (`smartpark.security.token-idle-timeout`).
3. **Check it:** `GET /api/auth/me` returns the current user, or 401 if the token is no longer valid.
4. **Sign out:** `POST /api/auth/logout` returns **204** and revokes the token immediately.

Tokens are random 256-bit values. The server keeps only their SHA-256 hashes, in memory: restarting the server signs everyone out. Deactivating an account revokes all its tokens.

### Role-based access control (enforced on the server)

Every `/api/**` request passes through `RoleCheckInterceptor`. Each controller method declares its roles with `@RequireRole`. The interceptor validates the token and then **re-reads the user from MongoDB**, so a role change or deactivation takes effect on the very next request. Hiding a button in the client is only a convenience: a modified client still gets 403.

| Operation | Allowed roles |
|---|---|
| `POST /api/auth/login`, `GET /api/health` | public (no token) |
| User management (`/api/users/**`), activity log, connected terminals | `ADMIN` |
| Category and slot changes (POST/PUT, disable/enable) | `ADMIN` |
| Vehicle entry | `ENTRY_OPERATOR`, `ADMIN` |
| Vehicle exit, exit search (`/api/parking/active/{v}`), history search (`/api/parking/sessions`) | `EXIT_OPERATOR`, `ADMIN` |
| Every other `GET` (slots, categories, active sessions, one session, statistics) | any signed-in user |

Missing, expired or revoked token: **401** `UNAUTHENTICATED`. Inactive account or wrong role: **403** `OPERATOR_NOT_AUTHORIZED`.

> **Development only:** with `smartpark.security.operator-header-enabled=true` (env `SMARTPARK_ALLOW_OPERATOR_HEADER=true`), the server also trusts an `X-Operator: <username>` header **without a password**. It is `false` by default and must stay off in real use; the automated tests enable it for brevity.

---

## Endpoints

### Vehicle categories

| Method | Path | Description | Success |
|---|---|---|---|
| GET | `/api/categories` | All categories, sorted by name | 200 |
| GET | `/api/categories/{name}` | One category (name is case-insensitive) | 200 |
| POST | `/api/categories` | Add a category (ADMIN) | **201** + `Location` |
| PUT | `/api/categories/{name}` | Replace description and compatible slot types (ADMIN) | 200 |

```jsonc
// POST /api/categories
{ "categoryName": "Light truck", "description": "Small trucks", "compatibleSlotTypes": ["LARGE"] }
// -> 201 { "id": "...", "categoryName": "LIGHT_TRUCK", "description": "Small trucks", "compatibleSlotTypes": ["LARGE"] }

// PUT /api/categories/VAN
{ "description": "Vans and SUVs", "compatibleSlotTypes": ["LARGE", "STANDARD"] }
```

Slot types: `STANDARD`, `LARGE`, `MOTORCYCLE`, `EV_CHARGING`, `ACCESSIBLE`.
When a category's compatible slot types change, every slot of a newly added type starts accepting the category, and every slot of a removed type stops accepting it. Both changes run in the same unit of work.

### Parking slots

| Method | Path | Description | Success |
|---|---|---|---|
| GET | `/api/slots?category=&status=&floor=` | All slots in map order (floor, row, column). Filters are optional and can be combined | 200 |
| GET | `/api/slots/available?category=CAR` | AVAILABLE slots, optionally only those accepting the category | 200 |
| GET | `/api/slots/{code}` | One slot | 200 |
| POST | `/api/slots` | Add a slot (ADMIN) | **201** + `Location` |
| PUT | `/api/slots/{code}` | Replace type, categories and position (ADMIN). Not allowed while OCCUPIED | 200 |
| POST | `/api/slots/{code}/disable` | AVAILABLE → DISABLED (ADMIN) | 200 |
| POST | `/api/slots/{code}/enable` | DISABLED → AVAILABLE (ADMIN) | 200 |

```jsonc
// POST /api/slots   (supportedVehicleCategories optional: derived from slotType if omitted)
{ "slotCode": "D-01", "slotType": "STANDARD", "floor": 1, "row": "D", "column": 1 }
// -> 201
{ "id": "...", "slotCode": "D-01", "slotType": "STANDARD",
  "supportedVehicleCategories": ["ACCESSIBLE", "CAR", "ELECTRIC_CAR"],
  "status": "AVAILABLE", "floor": 1, "row": "D", "column": 1, "currentTicketId": null }
```

### Vehicle entry

`POST /api/parking/entry` with header `Authorization: Bearer <token of entry1>`

```json
{ "vehicleNumber": "WP CAB-1234", "categoryName": "CAR", "slotCode": "A-01", "notes": "Roof rack fitted" }
```

`notes` is optional (max 200 characters). Blank notes are stored as `null`. Notes are returned in every session response.

Steps on the server:
1. Validate the input.
2. Check the operator's role.
3. Check the category exists.
4. Reject the vehicle if it already has an ACTIVE session.
5. **Atomically claim** the slot (it must be AVAILABLE and accept the category).
6. Generate a ticket ID and record the entry time.
7. Create the ACTIVE session.

```json
// 201 Created, Location: /api/parking/sessions/SP-20261113-000001
{
  "id": "6734...", "ticketId": "SP-20261113-000001",
  "vehicleNumber": "WPCAB1234", "vehicleCategory": "CAR", "parkingSlotCode": "A-01",
  "entryTime": "2026-11-13T08:15:30.123Z", "exitTime": null, "status": "ACTIVE",
  "entryOperator": "entry1", "exitOperator": null, "durationMinutes": null, "notes": "Roof rack fitted"
}
```

| Failure | Status | `code` |
|---|---|---|
| Invalid body | 400 | `VALIDATION_FAILED` |
| Vehicle already parked | 409 | `VEHICLE_ALREADY_PARKED` |
| Slot occupied or disabled, or taken a moment ago by another operator | 409 | `SLOT_UNAVAILABLE` |
| Slot does not accept this category | 422 | `SLOT_NOT_COMPATIBLE` |
| Unknown slot or category | 404 | `NOT_FOUND` |

Vehicle numbers are normalized: case, spaces and hyphens are ignored, so `wp cab-1234` is the same vehicle as `WPCAB1234`.

### Vehicle exit

1. **Search:** `GET /api/parking/active/{vehicleNumber}` returns the session, the assigned slot, and the minutes parked so far.

   ```json
   { "session": { "ticketId": "SP-20261113-000001", "vehicleNumber": "WPCAB1234", "...": "..." },
     "slot": { "slotCode": "A-01", "slotType": "STANDARD", "floor": 0, "row": "A", "column": 1, "...": "..." },
     "minutesParked": 42 }
   ```

   If the vehicle is not parked: **404** `NO_ACTIVE_SESSION`.

2. **Confirm:** `POST /api/parking/exit` with `Authorization: Bearer <token of exit1>` and body `{ "vehicleNumber": "WPCAB1234" }`.
   This marks the session COMPLETED, stores the exit time and releases the slot. It returns **200** with the completed session, including `exitTime` and `durationMinutes`. A second exit for the same vehicle gives **404** `NO_ACTIVE_SESSION`.

### Monitoring and records

| Method | Path | Description |
|---|---|---|
| GET | `/api/parking/sessions/active` | All parked vehicles (ACTIVE sessions), newest first |
| GET | `/api/parking/sessions?vehicleNumber=&status=&slotCode=&from=&to=&page=0&size=20` | History, newest first, paged. `from`/`to` filter on entry time (ISO-8601; `from` inclusive, `to` exclusive). `size` 1-200 |
| GET | `/api/parking/sessions/{ticketId}` | One session (receipt reprint) |
| GET | `/api/statistics` | Occupancy and traffic statistics |
| GET | `/api/activity?username=&role=&action=&limit=50` | Operator activity log, newest first (ADMIN). Optional filters: user, role (`ENTRY_OPERATOR` ...), action (`VEHICLE_ENTRY`, `LOGIN_SUCCESS` ...); limit 1-500 |
| GET | `/api/health` | Server status and server time |
| GET | `/api/realtime/status` | Clients connected to the WebSocket (operator, role, terminal, address, last heartbeat) |

### User management (ADMIN)

| Method | Path | Description | Success |
|---|---|---|---|
| GET | `/api/users` | All accounts, sorted by username (never includes password hashes) | 200 |
| POST | `/api/users` | Create an account `{ "username", "password", "role" }` | **201** + `Location` |
| PUT | `/api/users/{username}/role` | Assign a role `{ "role": "EXIT_OPERATOR" }`. Signs the user out everywhere | 200 |
| POST | `/api/users/{username}/activate` | Allow the account to sign in again | 200 |
| POST | `/api/users/{username}/deactivate` | Block the account and revoke its tokens | 200 |

Safety rules (**409** `INVALID_STATE`): an admin cannot deactivate or change the role of **their own** account, and the **last active admin** can never be deactivated or demoted, so the system always keeps an administrator. A duplicate username gives **409** `DUPLICATE_RESOURCE`. Every change is written to the activity log (`USER_CREATED`, `USER_ROLE_CHANGED`, `USER_ACTIVATED`, `USER_DEACTIVATED`).

Real-time events (WebSocket `ws://<server>:8080/ws`) are described in [REALTIME.md](REALTIME.md).

```jsonc
// GET /api/parking/sessions?status=COMPLETED&size=2
{ "content": [ { "ticketId": "...", "...": "..." }, { "...": "..." } ],
  "page": 0, "size": 2, "totalElements": 17, "totalPages": 9 }

// GET /api/statistics
{ "generatedAt": "...", "totalSlots": 30, "availableSlots": 27, "occupiedSlots": 2, "disabledSlots": 1,
  "occupancyRatePercent": 6.9, "activeSessions": 2, "entriesToday": 3, "exitsToday": 1,
  "averageStayMinutesToday": 12.5,
  "categories": [ { "categoryName": "CAR", "compatibleSlots": 19, "availableSlots": 18, "parkedVehicles": 1 } ],
  "floors": [ { "floor": 0, "totalSlots": 22, "availableSlots": 20, "occupiedSlots": 2, "disabledSlots": 0 } ] }
```

---

## Errors

Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json` body. The `code` field is stable, so clients can switch on it:

```json
{
  "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "Slot A-01 is already occupied",
  "instance": "/api/parking/entry",
  "code": "SLOT_UNAVAILABLE",
  "timestamp": "2026-11-13T08:15:30.456Z"
}
```

Validation errors also list the offending fields:

```json
{ "status": 400, "code": "VALIDATION_FAILED", "detail": "Request validation failed",
  "errors": [ { "field": "slotCode", "message": "must not be blank" },
              { "field": "vehicleNumber", "message": "letters, digits, spaces or '-', e.g. WP CAB-1234" } ] }
```

| Status | `code` values |
|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST` (bad JSON or unknown enum value), `INVALID_PARAMETER`, `BAD_REQUEST` |
| 401 | `UNAUTHENTICATED`, `INVALID_CREDENTIALS` |
| 403 | `OPERATOR_NOT_AUTHORIZED` |
| 404 | `NOT_FOUND`, `NO_ACTIVE_SESSION` |
| 409 | `SLOT_UNAVAILABLE`, `VEHICLE_ALREADY_PARKED`, `DUPLICATE_RESOURCE`, `INVALID_STATE`, `CONCURRENT_UPDATE` |
| 422 | `SLOT_NOT_COMPATIBLE` |
| 503 | `DATABASE_UNAVAILABLE` |
| 500 | `INTERNAL_ERROR` (details are only logged on the server) |

---

## Concurrency guarantees

| Situation | Mechanism | Result |
|---|---|---|
| Two operators submit the same slot | One atomic `findAndModify` with filter `status = AVAILABLE`, plus a partial unique index on ACTIVE sessions per slot | Exactly one **201**; the others get **409** `SLOT_UNAVAILABLE` |
| Same vehicle registered twice at once | Partial unique index on ACTIVE sessions per vehicle; the losing request's slot claim is rolled back or compensated | One **201**, one **409** `VEHICLE_ALREADY_PARKED` |
| Two exits for the same vehicle | Atomic `findAndModify` with filter `status = ACTIVE` | One **200**, one **404** `NO_ACTIVE_SESSION` |
| Admin edits a slot while it is being allocated | Optimistic locking (`version`) | **409** `CONCURRENT_UPDATE`; reload and retry |
| Multi-document consistency | MongoDB transaction when the server is a replica set; otherwise atomic steps with compensation, plus a reconciliation job | – |

These guarantees are verified by automated tests that fire 20 simultaneous HTTP requests at one slot (`ConcurrentEntryApiTest`, `ConcurrentSlotAllocationTest`).
