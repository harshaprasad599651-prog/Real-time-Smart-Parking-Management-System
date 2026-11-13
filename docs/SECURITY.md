# SmartPark security and reliability

How SmartPark protects accounts, data and the parking state, and how it behaves when something fails. Each point names the code that implements it and the tests that check it.

## 1. Accounts and passwords

| Measure | How | Where |
|---|---|---|
| Passwords stored as **BCrypt** hashes | Random salt per password, work factor 12 (`smartpark.security.bcrypt-strength`); the plain password is never stored or logged (`toString()` of request records hides it) | `PasswordConfig`, `UserService`, `CreateUserRequest` |
| Password policy | 8–72 characters (BCrypt uses 72 bytes) with at least one letter and one digit | `CreateUserRequest` |
| No username enumeration | Unknown user, wrong password and inactive account give the **same** 401 message, and every sign-in performs exactly one BCrypt check (a dummy hash for unknown accounts), so response times don't reveal which usernames exist | `UserService.authenticate` |
| **Brute-force protection** | 5 failures for one account within 15 min lock it for 5 min (429 `TOO_MANY_LOGIN_ATTEMPTS` + `Retry-After`), even for the correct password; one computer is locked after 20 failures across any usernames; success resets the account counter. Locks are logged as `LOGIN_LOCKED` | `LoginAttemptLimiter`, `AuthController` |

## 2. Sessions (sign-ins)

* A token is 256 random bits (`SecureRandom`), sent once at sign-in, then as `Authorization: Bearer …` on every REST call and the WebSocket handshake.
* The server stores only its **SHA-256 hash** (memory cache plus the `loginSessions` collection). Neither a memory dump nor a database copy yields usable tokens.
* Sliding expiry: 12 h without use. A TTL index removes expired sign-ins from MongoDB.
* Sign-ins survive a server restart, so terminals reconnect without signing in again.
* **Revocation is immediate.** Logout revokes the token. Deactivating an account or changing its role revokes all of its tokens **and closes its open WebSocket connections**. The client's reconnect is then refused and it returns to the login screen.
* `X-Operator` header sign-in (no password) exists only for automated tests and is off by default.

## 3. Authorization (server-side)

* Every `/api/**` request passes `RoleCheckInterceptor`, which reads the `@RequireRole` annotation on the controller method. The role is **re-read from MongoDB on every request**, so changes apply at once. Hiding buttons in the client is only a convenience.
* Services re-check the operator as well (`UserService.requireOperator`). For example, `ParkingSessionService.startParking` refuses an exit operator or a deactivated account even if called directly.
* WebSocket handshakes require a valid token and an active account. Admin-only data (activity log events) is sent only to ADMIN connections.
* A connection can only be replaced by a reconnect **of the same operator**. Before this stage, any operator could disconnect another operator's live connection by reusing its client id, which is visible in presence events. That is now fixed.
* Refused operations (403) are recorded as `ACCESS_DENIED` in the activity log, with the method, path and required roles.

## 4. Input validation and sanitisation

* Bean Validation on every request body and parameter: patterns for vehicle numbers, slot codes, usernames and category names; ranges for floors, bays, page sizes and limits. Errors are a 400 `VALIDATION_FAILED` listing the fields.
* Normalisation: vehicle numbers, slot codes, usernames and categories have one canonical form, so `wp cab-1234` and `WPCAB1234` are the same vehicle and the unique indexes work.
* **Free text is sanitised** (`Normalizer.freeText`) before it is stored or shown. This covers notes, category descriptions and printer names. Line breaks, control characters and invisible Unicode (zero-width, bidi overrides) become spaces, whitespace is collapsed, and length is capped.
* **Client-chosen labels** (terminal names at sign-in and in the WebSocket URL) are limited to safe characters and 40 characters. Client ids must look like ids.
* The activity log sanitises usernames and descriptions, so text typed at the login screen cannot forge extra log lines.
* MongoDB queries are built with typed `Criteria` values, never by concatenating strings, and no user input is used as a regular expression.

## 5. Parking state: duplicates, races, consistency

| Guarantee | Mechanism |
|---|---|
| One slot, one vehicle | The slot is claimed with one atomic `findAndModify` that only matches an AVAILABLE, compatible slot. A partial unique index on (slot, ACTIVE) is the database-level safety net |
| One vehicle, one active session | Pre-check plus a partial unique index on (vehicle, ACTIVE) |
| One exit wins | The ACTIVE→COMPLETED change is one atomic update |
| No half-done entries | On a replica set both writes run in a transaction. On a standalone server a failed second write is compensated, and a reconciliation job repairs a crash between the two writes |
| Slot edits vs allocations | Optimistic locking (`@Version`) → 409 `CONCURRENT_UPDATE` |
| Receipts | Created only by a successful entry, in the same insert. Reprints never create sessions |

Tests: `ConcurrentSlotAllocationTest` (20 operators, one slot; one vehicle, many slots; many vehicles, few slots; concurrent exits), `ConcurrentEntryApiTest`, and the new `ParkingConsistencyTest`:
* 12 terminals × 30 random entries/exits on 6 slots, 3 runs. Afterwards the slots and sessions must agree exactly: one ACTIVE session per OCCUPIED slot with the same ticket, and no free slot holding a ticket.
* Disabling a slot while it is being allocated: exactly one succeeds.
* Re-entry after exit gets a new ticket.
* Deactivated and wrong-role operators are refused.

## 6. Errors

* Every error is RFC 9457 `problem+json` with a stable `code` (see [API.md](API.md#errors)).
* Unexpected errors return 500 `INTERNAL_ERROR` without internals; the details go to the server log only.
* Duplicate keys that slip past the services return 409 `DUPLICATE_RESOURCE`, not 500.

## 7. Failures, timeouts and shutdown

| Situation | Behaviour |
|---|---|
| MongoDB down | Bounded driver timeouts (5 s to find the server, 15 s per reply) → **503 `DATABASE_UNAVAILABLE` within about 5 s** instead of hanging. `/api/health` reports `DEGRADED / database DOWN`. The login screen shows *Database unavailable*. The driver reconnects on its own; verified: service resumed about 1 s after MongoDB returned, without restarting the server |
| Wrong database password | The server refuses to start with an authentication error; the password is masked in the log |
| Slow network | Client: 4 s connect / 10 s request timeouts. Server: idle HTTP connections time out (20 s); a WebSocket client that cannot keep up is disconnected and catches up after reconnecting |
| Entry request without an answer | The Entry screen **checks** whether the vehicle was registered instead of claiming "nothing was saved"; the Exit screen already did this. A blind retry would be refused by the server anyway (`VEHICLE_ALREADY_PARKED`) |
| Network drop / server restart | Automatic WebSocket reconnection with backoff, replay of missed events, and a full data reload after every reconnect ([REALTIME.md](REALTIME.md)) |
| Server shutdown | `server.shutdown=graceful`: requests in progress (e.g. an entry being saved) finish within 20 s. WebSocket clients receive *1001 Server shutting down* and start reconnecting. Background jobs stop with the application context |
| Client shutdown | Closing the window signs out on the server, closes the WebSocket and HTTP client, and stops the client's background threads |

## 8. Network and database exposure

Only TCP 8080 is reachable from the LAN. MongoDB listens on 127.0.0.1 with authentication, and the backend account may only read and write `smartpark_db` ([DEPLOYMENT.md](DEPLOYMENT.md)). Secrets live in `application-local.properties` on the Admin PC, which is git-ignored.

**Not included** (acceptable for a closed LAN, worth adding for a wider network): HTTPS/WSS (traffic, including passwords at sign-in, is unencrypted on the LAN), persistence of the login limiter across restarts, and password expiry or two-factor sign-in.
