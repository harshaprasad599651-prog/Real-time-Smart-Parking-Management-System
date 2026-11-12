# SmartPark – Real-Time Smart Parking Management System

University **Network Programming** project. Three computers on the same LAN/Wi-Fi share one central server:

| Computer | Runs |
|---|---|
| **Admin PC** | `smartpark-server` (Spring Boot + MySQL) **and** `smartpark-client` (admin dashboard) |
| **Entry PC** | `smartpark-client` (entry operator) |
| **Exit PC**  | `smartpark-client` (exit operator) |

Clients send commands to the server through a **REST API (HTTP/JSON)** and receive real-time updates through a **WebSocket** (`ws://<server>:8080/ws`).

> **Current stage:** project skeleton only. The server starts and exposes `GET /api/health` plus a WebSocket endpoint. The client shows a welcome screen with a *Check server connection* button. Parking features are not implemented yet.

---

## Technology stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Build | Maven (multi-module, Maven Wrapper included) |
| Server | Spring Boot 4.1 · Spring Web MVC · Spring WebSocket · Spring Data JPA · Bean Validation |
| Database | MySQL 8 (MySQL Connector/J) |
| Desktop client | JavaFX 21 (Controls + FXML) · Java HTTP Client (`java.net.http`) |

---

## Project structure

```
SmartPark/
├── pom.xml                         Parent POM: Java version, dependency and plugin versions
├── mvnw, mvnw.cmd, .mvn/           Maven Wrapper (no Maven installation required)
│
├── smartpark-server/               Spring Boot server (runs on the Admin PC)
│   ├── application-local.yml.example   Template for local secrets (copy to application-local.yml)
│   └── src/main/java/com/smartpark/server/
│       ├── SmartParkServerApplication.java
│       ├── config/        Spring configuration (WebSocketConfig)
│       ├── controller/    REST controllers (HealthController)
│       ├── dto/           Request/response objects
│       ├── entity/        JPA entities                 (later phases)
│       ├── repository/    Spring Data JPA repositories (later phases)
│       ├── service/       Business logic / transactions (later phases)
│       ├── websocket/     Real-time WebSocket hub
│       ├── security/      Authentication                (later phases)
│       ├── scheduler/     Background jobs                (later phases)
│       └── exception/     Error handling                 (later phases)
│
└── smartpark-client/               JavaFX desktop client (runs on all three PCs)
    └── src/main/
        ├── java/module-info.java
        ├── java/com/smartpark/client/
        │   ├── app/       JavaFX Application entry point
        │   ├── config/    ClientConfig (server address, terminal name)
        │   ├── net/       REST client built on java.net.http.HttpClient
        │   ├── ui/        FXML controllers
        │   ├── realtime/  WebSocket client                 (later phases)
        │   ├── state/     Observable client-side state     (later phases)
        │   ├── print/     Receipt printing                 (later phases)
        │   └── util/      Helpers
        └── resources/
            ├── fxml/welcome.fxml
            ├── css/smartpark.css
            └── smartpark-client.properties   Default client settings
```

---

## Prerequisites

1. **JDK 21 or newer.** Check with `java -version`.
2. **MySQL 8.x**, only on the Admin PC (the server needs it to start).
3. **Maven is optional.** The included wrapper (`mvnw` / `mvnw.cmd`) downloads Maven 3.9 automatically on first use. The first build also needs internet access to download dependencies.

> On Windows use `mvnw.cmd ...` (PowerShell: `.\mvnw.cmd ...`). On Linux/macOS use `./mvnw ...`.

---

## 1. Database setup (Admin PC, one time)

Log in to MySQL as root and create a dedicated user:

```sql
CREATE DATABASE IF NOT EXISTS smartpark CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'smartpark'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON smartpark.* TO 'smartpark'@'localhost';
FLUSH PRIVILEGES;
```

Then give the server the password **without committing it**. Use one of these:

- **Option A – local file (recommended):** copy `smartpark-server/application-local.yml.example` to `smartpark-server/application-local.yml` and set the password. This file is git-ignored.
- **Option B – environment variables:**

  ```powershell
  $env:SMARTPARK_DB_USERNAME = "smartpark"
  $env:SMARTPARK_DB_PASSWORD = "choose-a-password"
  ```

| Variable | Default |
|---|---|
| `SMARTPARK_DB_HOST` | `localhost` |
| `SMARTPARK_DB_PORT` | `3306` |
| `SMARTPARK_DB_NAME` | `smartpark` |
| `SMARTPARK_DB_USERNAME` | `smartpark` |
| `SMARTPARK_DB_PASSWORD` | *(empty)* |
| `SMARTPARK_SERVER_PORT` | `8080` |

---

## 2. Build everything

From the project root:

```powershell
.\mvnw.cmd clean verify
```

This compiles both modules, runs the unit tests and produces:

- `smartpark-server/target/smartpark-server.jar` (executable Spring Boot jar)
- `smartpark-client/target/smartpark-client.jar`

---

## 3. Run the server (Admin PC)

From the **`smartpark-server` folder** (so `application-local.yml` is found):

```powershell
cd smartpark-server
..\mvnw.cmd spring-boot:run
```

or run the built jar:

```powershell
cd smartpark-server
java -jar target\smartpark-server.jar
```

The server is ready when the log shows `Started SmartParkServerApplication`. Verify it:

```powershell
curl http://localhost:8080/api/health
# {"status":"UP","service":"smartpark-server","serverTime":"..."}
```

Stop the server with **Ctrl+C**.

---

## 4. Run the client (any PC)

From the project root:

```powershell
.\mvnw.cmd -pl smartpark-client javafx:run
```

(or from inside `smartpark-client`: `..\mvnw.cmd javafx:run`)

The welcome screen opens. Click **Check server connection** to test the link to the server.

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

## 5. LAN checklist (three computers)

1. Connect all three PCs to the **same** network and give the Admin PC a **fixed IP** (static IP or a DHCP reservation in the router).
2. Find the Admin PC's IP with `ipconfig` (IPv4 Address).
3. Allow the server port through Windows Defender Firewall on the Admin PC. Run in an **administrator** PowerShell:

   ```powershell
   New-NetFirewallRule -DisplayName "SmartPark Server 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
   ```

   Make sure the Wi-Fi network profile is set to **Private**.
4. **Do not** open MySQL port 3306. Only the server talks to the database.
5. From the Entry or Exit PC, open `http://<admin-ip>:8080/api/health` in a browser, then start the client with that IP.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Server fails with `Communications link failure` | MySQL is not running, or the host/port is wrong. |
| Server fails with `Access denied for user` | Wrong DB username/password. Check `application-local.yml` or the environment variables. |
| `Port 8080 was already in use` | Another program uses 8080. Start with `SMARTPARK_SERVER_PORT=8081` and point the clients at that port. |
| Client shows *connection refused* | The server isn't running, or the wrong IP/port is set. |
| Client shows *timed out* (from another PC) | Firewall is blocking 8080, or the PCs are on different networks or the Wi-Fi has client isolation enabled. |
