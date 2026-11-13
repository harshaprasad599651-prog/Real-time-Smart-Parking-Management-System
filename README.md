# SmartPark – Real-Time Smart Parking Management System

University **Network Programming** project. Three computers on the same LAN/Wi-Fi share one central server:

| Computer | Runs |
|---|---|
| **Admin PC** | MongoDB Community Server + `smartpark-server` (Spring Boot) **and** `smartpark-client` (admin dashboard) |
| **Entry PC** | `smartpark-client` (entry operator) |
| **Exit PC**  | `smartpark-client` (exit operator) |

Clients send commands to the server through a **REST API (HTTP/JSON)** and receive real-time updates through a **WebSocket** (`ws://<server>:8080/ws`). **Only the server connects to MongoDB.** The Entry and Exit clients never talk to the database directly.

> **Current stage:** admin dashboard. Entry operators register a vehicle (with optional notes), pick a compatible free slot on a live graphical parking map, confirm, and get the ticket with a prepared receipt (physical printing comes later). Exit operators search a vehicle, review its active session, and confirm the exit. The server completes it transactionally, and every terminal sees the slot become free at once. Admins have a full management dashboard: live KPIs and charts, a live map showing which vehicle is in each slot, parked vehicles, searchable parking history, slot management, operator account management and live operator activity. Role-based access control is enforced on the server for every endpoint.
>
> **Documentation:** [docs/API.md](docs/API.md) (REST) · [docs/REALTIME.md](docs/REALTIME.md) (WebSocket, REST vs WebSocket, test client) · Swagger UI at `http://localhost:8080/swagger-ui.html` while the server runs · sample requests in [smartpark-server/http/smartpark-api.http](smartpark-server/http/smartpark-api.http)

---

## Technology stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Build | Maven (multi-module, Maven Wrapper included) |
| Server | Spring Boot 4.1 · Spring Web MVC · Spring WebSocket · **Spring Data MongoDB** · Bean Validation · BCrypt (spring-security-crypto) · springdoc-openapi (Swagger UI) |
| Database | **MongoDB Community Server 8.0**, installed locally on the Admin PC (not Atlas) |
| Desktop client | JavaFX 21 (Controls + FXML) · Java HTTP Client (`java.net.http`) |

---

## Project structure

```
SmartPark/
├── pom.xml                         Parent POM: Java version, dependency and plugin versions
├── mvnw, mvnw.cmd, .mvn/           Maven Wrapper (no Maven installation required)
│
├── smartpark-server/               Spring Boot server (runs on the Admin PC)
│   ├── application-local.properties.example   Template for local overrides (git-ignored copy)
│   └── src/main/java/com/smartpark/server/
│       ├── SmartParkServerApplication.java
│       ├── config/        MongoConfig (transaction manager), WebSocketConfig, SchedulingConfig
│       ├── document/      @Document classes + enums (User, VehicleCategory, ParkingSlot, ParkingSession, ActivityLog, Counter)
│       ├── repository/    MongoRepository interfaces
│       ├── service/       Business logic: UserService, VehicleCategoryService, ParkingSlotService,
│       │                  ParkingSessionService (atomic allocation), ActivityLogService, TicketIdGenerator
│       ├── persistence/   MongoIndexInitializer, MongoTransactionRunner, index names
│       ├── dto/           Request/response records with Bean Validation constraints
│       ├── bootstrap/     DataSeeder (sample users, categories, slots)
│       ├── scheduler/     SlotReconciliationJob
│       ├── security/      PasswordConfig (BCrypt)
│       ├── exception/     Domain exceptions
│       ├── util/          Normalizer (vehicle numbers, codes, usernames)
│       ├── controller/    REST controllers (categories, slots, parking, monitoring, realtime status, health)
│       ├── event/         Domain events published by services after a committed change
│       └── websocket/     RealtimeHub (broadcast, replay, heartbeat), handler, handshake check, event relay
│
└── smartpark-client/               JavaFX desktop client (runs on all three PCs)
    └── src/main/
        ├── java/module-info.java
        ├── java/com/smartpark/client/
        │   ├── app/       SmartParkClientApp (start-up wiring), AppContext (shared services)
        │   ├── config/    ClientConfig (server address, terminal name)
        │   ├── net/       ApiClient (Java HttpClient + Jackson, bearer token, problem+json errors), DTOs
        │   ├── session/   SessionManager (login/logout/expiry), UserSession, UserRole
        │   ├── service/   RealtimeService (WebSocket -> JavaFX properties and FX-thread events)
        │   ├── realtime/  RealtimeClient: WebSocket with heartbeat, reconnect, replay, de-duplication
        │   ├── parking/   SlotMapModel (map state + selection rules), ReceiptData
        │   ├── ui/
        │   │   ├── navigation/  View (screens + allowed roles), AccessPolicy, Navigator
        │   │   ├── login/       LoginController
        │   │   ├── shell/       MainLayoutController (sidebar, top bar, status bar)
        │   │   ├── dashboard/   Admin overview (KPIs, usage bars, live map, activity)
        │   │   ├── parking/     Vehicle entry, vehicle exit, receipt preview + printing
        │   │   ├── connections/ Connected terminals (admin)
        │   │   ├── common/      LiveView base class, access-denied screen
        │   │   └── component/   SlotMapView, StatCard, ActivityFeed, ConnectionIndicator, Icons
        │   ├── tools/     RealtimeTestClient (command-line demo / live monitor)
        │   └── util/      Formats
        └── resources/
            ├── fxml/      login, main-layout, dashboard/*, parking/*, connections, access-denied
            ├── css/smartpark.css             Theme (colours, typography, components)
            └── smartpark-client.properties   Default client settings
```

---

## Prerequisites

1. **JDK 21 or newer.** Check with `java -version`.
2. **MongoDB Community Server**, only on the Admin PC. See below.
3. **Maven is optional.** The included wrapper (`mvnw` / `mvnw.cmd`) downloads Maven 3.9 automatically on first use. The first build also needs internet access to download dependencies.

> On Windows use `mvnw.cmd ...` (PowerShell: `.\mvnw.cmd ...`). On Linux/macOS use `./mvnw ...`.

---

## 1. Install and start MongoDB (Admin PC, one time)

### Windows

1. Download **MongoDB Community Server** (version 8.0, platform *Windows x64*, package *msi*) from <https://www.mongodb.com/try/download/community>.
2. Run the installer and choose **Complete**. Keep **"Install MongoD as a Service"** ticked ("Run service as Network Service user"). The default data folder `C:\Program Files\MongoDB\Server\8.0\data` is fine.
   Optionally tick *Install MongoDB Compass* (a GUI to browse the data).
3. MongoDB now starts automatically with Windows. To check, stop or start it, open PowerShell **as Administrator**:

   ```powershell
   Get-Service MongoDB          # Status should be "Running"
   Start-Service MongoDB
   Stop-Service MongoDB
   ```

4. Test the connection. In Compass connect to `mongodb://localhost:27017`, or install **mongosh** (<https://www.mongodb.com/try/download/shell>) and run:

   ```powershell
   mongosh "mongodb://localhost:27017" --eval "db.runCommand({ ping: 1 })"
   ```

You do **not** need to create the database or collections. On first start the server creates `smartpark_db`, all collections and indexes, and loads the sample data.


### Linux (Ubuntu) / macOS

Follow <https://www.mongodb.com/docs/manual/administration/install-community/>, then `sudo systemctl start mongod` (Linux) or `brew services start mongodb-community` (macOS).

### Security notes

- The installer binds MongoDB to `127.0.0.1` (`bindIp` in `mongod.cfg`). **Keep it that way.** Only the SmartPark server, on the same PC, needs it. Do **not** open port 27017 in the firewall.
- A default local install has no username/password. If you enable authentication, put the credentials in the URI (see §2).

### Optional: enable multi-document transactions

A default install is a *standalone* server, which **does not support transactions**. SmartPark is designed for this. It uses atomic single-document operations plus compensation (see [Concurrency design](#concurrency-design)). If you want transactions as an extra layer, turn the server into a one-node replica set:

1. Edit `C:\Program Files\MongoDB\Server\8.0\bin\mongod.cfg` and add:

   ```yaml
   replication:
     replSetName: rs0
   ```

2. Restart the service (`Restart-Service MongoDB`, as Administrator) and run once:

   ```powershell
   mongosh --eval "rs.initiate({ _id: 'rs0', members: [{ _id: 0, host: '127.0.0.1:27017' }] })"
   ```

3. Start the server with `SMARTPARK_MONGODB_URI=mongodb://127.0.0.1:27017/?replicaSet=rs0`.
   The log then shows `MongoDB multi-document transactions: ENABLED (replica set detected)`.

---

## 2. Server configuration

Defaults are in `smartpark-server/src/main/resources/application.properties`. Each setting can be overridden with an **environment variable**, or with a git-ignored `smartpark-server/application-local.properties` (copy `application-local.properties.example`).

| Environment variable | Default | Purpose |
|---|---|---|
| `SMARTPARK_MONGODB_URI` | `mongodb://localhost:27017` | MongoDB connection string |
| `SMARTPARK_MONGODB_DATABASE` | `smartpark_db` | Database name |
| `SMARTPARK_MONGODB_TRANSACTIONS` | `auto` | `auto` = use transactions if the server is a replica set; `never` = always use the atomic/compensation path |
| `SMARTPARK_SEED_ENABLED` | `true` | Create sample data on first start (only into empty collections) |
| `SMARTPARK_SEED_ADMIN_PASSWORD` | `Admin@123` | Password of the sample `admin` account |
| `SMARTPARK_SEED_ENTRY_PASSWORD` | `Entry@123` | Password of the sample `entry1` account |
| `SMARTPARK_SEED_EXIT_PASSWORD` | `Exit@123` | Password of the sample `exit1` account |
| `SMARTPARK_SERVER_PORT` | `8080` | HTTP/WebSocket port |

PowerShell example:

```powershell
$env:SMARTPARK_MONGODB_URI = "mongodb://localhost:27017"
$env:SMARTPARK_SEED_ADMIN_PASSWORD = "MyStrongPassword!"
```

> Seed passwords apply only when the accounts are first created. Change them before any real use.

---

## 3. Data model (database `smartpark_db`)

| Collection | Fields | Unique indexes |
|---|---|---|
| `users` | id, username, passwordHash (BCrypt), role (`ADMIN` / `ENTRY_OPERATOR` / `EXIT_OPERATOR`), active, createdAt | `username` |
| `vehicleCategories` | id, categoryName, description, compatibleSlotTypes | `categoryName` |
| `parkingSlots` | id, slotCode, slotType, supportedVehicleCategories, status (`AVAILABLE` / `OCCUPIED` / `DISABLED`), floor, row, column, currentTicketId, statusChangedAt, version | `slotCode`; `(floor, row, column)` |
| `parkingSessions` | id, ticketId, vehicleNumber, vehicleCategory, parkingSlotCode, entryTime, exitTime, status (`ACTIVE` / `COMPLETED`), entryOperator, exitOperator | `ticketId`; **`vehicleNumber` where status = ACTIVE**; **`parkingSlotCode` where status = ACTIVE** |
| `activityLogs` | id, username, action, description, timestamp | none (indexed by time and by user) |
| `counters` | internal per-day ticket counters | – |

Vehicle numbers are normalized (`wp cab-1234` → `WPCAB1234`), so formatting differences can't create duplicates.

### Sample data (created on first start)

| Users | Categories | Slots (30) |
|---|---|---|
| `admin` / `Admin@123` (ADMIN)<br>`entry1` / `Entry@123` (ENTRY_OPERATOR)<br>`exit1` / `Exit@123` (EXIT_OPERATOR) | CAR, MOTORCYCLE, VAN, ELECTRIC_CAR, ACCESSIBLE | Floor 0: `A-01..A-08` standard, `B-01..B-04` large, `B-05..B-06` EV charging, `B-07..B-08` accessible, `M-01..M-06` motorcycle<br>Floor 1: `C-01..C-08` standard |

### Concurrency design

Two operators can never get the same slot:

1. **Atomic claim.** One `findAndModify` sets a slot to `OCCUPIED` *only if* it is currently `AVAILABLE` and supports the vehicle category. MongoDB applies single-document updates atomically, so exactly one concurrent request matches. The others are told the slot was taken.
2. **Database-enforced rules.** Partial unique indexes allow only one `ACTIVE` session per vehicle and per slot. They reject a duplicate even if application code is bypassed.
3. **Consistency without transactions.** On a standalone server, if creating the session fails after the slot was claimed, the claim is undone (guarded by the request's ticket id). A background job releases any slot left `OCCUPIED` without an active session (possible only after a crash), but only once it has been unchanged for 2 minutes. On a replica set, both writes run inside one transaction instead.

---

## 4. Build and test

From the project root:

```powershell
.\mvnw.cmd clean verify
```

The server's integration tests run against a **real MongoDB**, using the separate database `smartpark_test_db` (never `smartpark_db`). They cover connectivity, document round-trips, indexes, duplicate prevention and concurrent slot allocation (20 operators racing for one slot). If MongoDB is not running, these tests are **skipped**, not failed. To test against another server:

```powershell
$env:SMARTPARK_TEST_MONGODB_URI = "mongodb://127.0.0.1:27017/?replicaSet=rs0"
.\mvnw.cmd -pl smartpark-server test
```

---

## 5. Run the server (Admin PC)

Make sure MongoDB is running, then from the **`smartpark-server` folder** (so `application-local.properties` is found):

```powershell
cd smartpark-server
..\mvnw.cmd spring-boot:run
```

or run the built jar:

```powershell
cd smartpark-server
java -jar target\smartpark-server.jar
```

On first start the log shows `MongoDB indexes verified on database 'smartpark_db'` and `Seeded sample data: 3 users, 5 categories, 30 slots`. Check it is up:

```powershell
curl http://localhost:8080/api/health
# {"status":"UP","service":"smartpark-server","serverTime":"..."}
```

Stop the server with **Ctrl+C**.

---

## 6. Run the client (any PC)

From the project root:

```powershell
.\mvnw.cmd -pl smartpark-client javafx:run
```

(or from inside `smartpark-client`: `..\mvnw.cmd javafx:run`)

The login screen opens and shows whether the server is reachable. Sign in with one of the sample accounts:

| Username | Password | Lands on | Sidebar |
|---|---|---|---|
| `admin` | `Admin@123` | Overview | Overview, Vehicle Entry, Vehicle Exit, Terminals |
| `entry1` | `Entry@123` | Vehicle Entry | Vehicle Entry |
| `exit1` | `Exit@123` | Vehicle Exit | Vehicle Exit |

The top bar shows the live connection to the server (green **Live**, amber **Reconnecting...**, red **Offline**). If the session ends, because the token expired, the server restarted or the account was deactivated, the client returns to the login screen with a message. See [docs/CLIENT.md](docs/CLIENT.md) for how the client is built.

### Pointing a client at the Admin PC

By default the client connects to `localhost:8080`. On the Entry and Exit PCs, set the Admin PC's IP address in one of these ways:

- **Command line:**

  ```powershell
  .\mvnw.cmd -pl smartpark-client javafx:run "-Dsmartpark.server.host=192.168.1.10" "-Dsmartpark.terminal.name=ENTRY-GATE-1"
  ```

- **Per-PC file:** create `smartpark-client/smartpark-client.properties` (git-ignored):

  ```properties
  smartpark.server.host=192.168.1.10
  smartpark.server.port=8080
  smartpark.terminal.name=EXIT-GATE-1
  ```

---

## 7. LAN checklist (three computers)

1. Connect all three PCs to the **same** network and give the Admin PC a **fixed IP** (static IP or a DHCP reservation in the router).
2. Find the Admin PC's IP with `ipconfig` (IPv4 Address).
3. Allow the server port through Windows Defender Firewall on the Admin PC. Run in an **administrator** PowerShell:

   ```powershell
   New-NetFirewallRule -DisplayName "SmartPark Server 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
   ```

   Make sure the Wi-Fi network profile is set to **Private**.
4. **Do not** open MongoDB port 27017. Only the server, on the same PC, talks to the database.
5. From the Entry or Exit PC, open `http://<admin-ip>:8080/api/health` in a browser, then start the client with that IP.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Server log: `Timed out after 30000 ms while waiting to connect` / `Connection refused` to `localhost:27017` | MongoDB is not running. Run `Get-Service MongoDB`, then `Start-Service MongoDB` (as Administrator). |
| Server fails with `Command failed with error 13 (Unauthorized)` | MongoDB has authentication enabled. Put the username/password in `SMARTPARK_MONGODB_URI`. |
| Server fails with `Index ... already exists with different options` or `E11000` during start | The database contains data that conflicts with the unique indexes (e.g. created by hand). Fix or remove the duplicates, or use a fresh database name. |
| Integration tests show as *skipped* | MongoDB was not reachable at `SMARTPARK_TEST_MONGODB_URI` (default `localhost:27017`). |
| `Port 8080 was already in use` | Another program uses 8080. Start with `SMARTPARK_SERVER_PORT=8081` and point the clients at that port. |
| Client shows *connection refused* | The server isn't running, or the wrong IP/port is set. |
| Client shows *timed out* (from another PC) | Firewall is blocking 8080, or the PCs are on different networks or the Wi-Fi has client isolation enabled. |
