# Deploying SmartPark on three Windows PCs (LAN / Wi-Fi)

SmartPark runs entirely on your local network. No cloud service and no Internet connection is needed.

```
          Admin PC (Computer 1)                      Entry PC (Computer 2)
 ┌──────────────────────────────────────┐         ┌──────────────────────┐
 │ MongoDB 127.0.0.1:27017  (private)   │         │ SmartPark client     │
 │        ▲  password login             │  REST + │ terminal ENTRY-GATE-1│
 │ SmartPark server  0.0.0.0:8080  ◄────┼─────────┤                      │
 │        ▲                             │WebSocket└──────────────────────┘
 │ SmartPark client (ADMIN, localhost)  │         ┌──────────────────────┐
 └──────────────────────────────────────┘◄────────┤ SmartPark client     │
                                                  │ terminal EXIT-GATE-1 │
                                                  │ Exit PC (Computer 3) │
                                                  └──────────────────────┘
```

* **Single source of truth:** only the server talks to MongoDB. Clients never store parking data; they load it from the server and are kept up to date by WebSocket events.
* **One port:** REST (`http://<admin-ip>:8080/api/...`) and WebSocket (`ws://<admin-ip>:8080/ws`) share TCP **8080**.
* **Database:** SmartPark uses **MongoDB Community Server** (not MySQL). It listens on 127.0.0.1 only and requires a password.

---

## 1. Build the packages (development PC)

```cmd
deploy\build-release.cmd
```

This runs all tests and creates:

| Package | Copy to | Contents |
|---|---|---|
| `dist\SmartPark-Server.zip` | Admin PC | `smartpark-server.jar`, `start-server.cmd`, `open-firewall.cmd`, `application-local.properties.example`, `mongodb\` scripts |
| `dist\SmartPark-Client.zip` | all three PCs | `SmartPark.cmd`, `Test-Connection.cmd`, `smartpark-client.properties`, `receipt-template.properties`, `runtime\` (Java + JavaFX included) |

The client package contains its own Java runtime, so the Entry and Exit PCs need **nothing installed**. The Admin PC needs a **Java 21 or newer runtime** for the server (for example Eclipse Temurin 21 JRE).

---

## 2. Network preparation (all PCs)

1. Connect the three PCs to the **same router / Wi-Fi network**. Guest Wi-Fi networks often block traffic between devices ("client isolation"); use the main network.
2. In *Settings › Network & Internet*, set the network profile to **Private** on every PC.
3. Give the Admin PC an **address that does not change**: either a DHCP reservation in the router (recommended) or a static IPv4 address in the adapter settings. Clients use this address.
4. Find the Admin PC's address: `ipconfig` → *IPv4 Address*, e.g. `192.168.1.10`. The server also prints it at start-up.

### Firewall and ports

| PC | Direction | Port | Purpose | Action |
|---|---|---|---|---|
| Admin | inbound | **TCP 8080** | SmartPark REST API + WebSocket | allow from the local subnet: run `open-firewall.cmd` **as Administrator** |
| Admin | – | TCP 27017 | MongoDB | **do not open.** MongoDB listens on 127.0.0.1 only |
| Entry / Exit | outbound | TCP 8080 | to the Admin PC | allowed by default in Windows; nothing to do |
| Admin client | – | localhost | client on the same PC as the server | nothing to do |

`open-firewall.cmd` adds one rule, *SmartPark Server*: inbound TCP 8080, Private profile, remote addresses = local subnet. If you use a third-party antivirus firewall, allow the same port there. Remove the rule with `netsh advfirewall firewall delete rule name="SmartPark Server"`.

---

## 3. Admin PC (Computer 1)

### 3.1 MongoDB (secure local database)

1. Install **MongoDB Community Server** as a Windows service (default options). Compass is optional but convenient.
2. **Create the accounts.** Open `mongodb\create-smartpark-user.js`, replace the two `CHANGE-ME` passwords, then run it:
   * Compass → connect to `mongodb://localhost:27017` → **>_ MONGOSH** panel → paste the file → Enter, or
   * `mongosh "mongodb://localhost:27017" --file create-smartpark-user.js`

   This creates `smartparkAdmin` (user administration) and `smartpark`, which the backend uses. `smartpark` has **readWrite on `smartpark_db` only**.
3. **Lock it down.** As Administrator, edit `C:\Program Files\MongoDB\Server\<version>\bin\mongod.cfg` so it contains the two sections from `mongodb\mongod.cfg.example`:
   ```yaml
   net:
     port: 27017
     bindIp: 127.0.0.1
   security:
     authorization: enabled
   ```
   Restart the service (`services.msc` → *MongoDB Server* → Restart).

### 3.2 SmartPark server

1. Unzip `SmartPark-Server.zip`, for example to `C:\SmartPark\SmartPark-Server`.
2. Copy `application-local.properties.example` to **`application-local.properties`** and set:
   ```properties
   # MongoDB account from step 3.1 (URL-encode special characters: @ -> %40, : -> %3A, / -> %2F, % -> %25)
   spring.mongodb.uri=mongodb://smartpark:YOUR-APP-PASSWORD@127.0.0.1:27017/smartpark_db?authSource=smartpark_db
   spring.mongodb.database=smartpark_db

   # Passwords of the first accounts (used only when the database is empty)
   smartpark.seed.admin-password=...
   smartpark.seed.entry-password=...
   smartpark.seed.exit-password=...
   ```
   This file holds secrets: keep it on the Admin PC only and never commit it (it is git-ignored).
3. Optional: `server.address=192.168.1.10` makes the server listen **only** on the LAN address. The default `0.0.0.0` listens on all adapters, so the Admin client can use `localhost` and the others the LAN IP.
4. Run **`open-firewall.cmd` as Administrator** (once).
5. Start the server with **`start-server.cmd`**. When it is ready it prints:
   ```
   SmartPark server is listening on 0.0.0.0:8080
   LAN clients: smartpark.server.host=192.168.1.10   smartpark.server.port=8080   (REST http://192.168.1.10:8080/api, WebSocket ws://192.168.1.10:8080/ws)
   ```
   Keep the window open; Ctrl+C stops the server.

### 3.3 Admin client

Unzip `SmartPark-Client.zip` to `C:\SmartPark\SmartPark-Client`, edit `smartpark-client.properties`:

```properties
smartpark.server.host=localhost
smartpark.server.port=8080
smartpark.terminal.name=ADMIN
```

Start **`SmartPark.cmd`** and sign in as `admin`.

---

## 4. Entry PC (Computer 2) and Exit PC (Computer 3)

1. Unzip `SmartPark-Client.zip` to `C:\SmartPark\SmartPark-Client`.
2. Edit `smartpark-client.properties` with Notepad:

   | Setting | Entry PC | Exit PC |
   |---|---|---|
   | `smartpark.server.host` | Admin PC's LAN IP, e.g. `192.168.1.10` | same |
   | `smartpark.server.port` | `8080` | `8080` |
   | `smartpark.terminal.name` | `ENTRY-GATE-1` | `EXIT-GATE-1` |
   | `smartpark.printer.name` | receipt printer name (empty = default printer) | – |

   No recompiling is needed: the client reads this file at every start. A single setting can also be overridden for one run with a JVM option, e.g. `-Dsmartpark.server.host=192.168.1.20`.
3. Double-click **`Test-Connection.cmd`**. It must print `OK: server UP`. If it fails, see *Troubleshooting*.
4. Start **`SmartPark.cmd`** and sign in (`entry1` on the Entry PC, `exit1` on the Exit PC). The login screen already shows whether the server is reachable.
5. Optional: create a desktop shortcut to `SmartPark.cmd`.

---

## 5. Connection status, disconnections and server restarts

Every client shows its connection state at all times:

* **Login screen:** *Online* / *Unreachable* next to the server address.
* **Every dashboard (top bar):** a coloured indicator with text: green **Live**, amber **Reconnecting…**, red **Offline**. Hovering it shows details, e.g. the next retry.

What happens when the network or the server goes away:

| Event | Client behaviour |
|---|---|
| Wi-Fi drops, cable unplugged, server stopped | The socket error is noticed at once, or the heartbeat detects silence within about 50 s (15 s heartbeat × 3, checked every 5 s); the indicator turns amber and the client **reconnects automatically** with growing, jittered delays (about 1 s, doubling up to 30 s), so terminals don't all retry together. |
| Entry operator tries to allocate while offline | Allocation is **paused** with a clear banner; nothing can be decided from a stale map. REST errors say "server cannot be reached; nothing was saved". |
| Connection back, same server | Missed events are **replayed** from the server's buffer, and every open screen **reloads its data from the server**. |
| Server restarted | The client detects the new server instance, reconnects, and **reloads all data** (full resync). Sign-ins are stored (as hashes) in MongoDB, so the operators stay signed in; there is nothing to do on the Entry and Exit PCs. |
| Session ended on purpose (logout, account deactivated, role changed, 12 h unused) | The client returns to the login screen with "Your session has expired or was ended by the server". |

Slots are allocated by the server only, atomically in MongoDB, so two gates can never get the same slot, even while a client is reconnecting.

---

## 6. Acceptance test (three PCs)

1. Admin PC: server running, Admin client signed in, *Overview* open. Entry PC: `entry1`. Exit PC: `exit1`.
2. **Entry PC:** enter vehicle `WP CAB-1234`, category *Car*, click slot **A-01**, *Confirm allocation* → ticket issued, receipt preview.
3. **Admin PC:** A-01 turns **red** on the live map with `WPCAB1234` on the tile; Occupied +1; the activity feed shows the entry. No refresh needed.
4. **Exit PC:** search `WPCAB1234` → active session at A-01 → *Confirm exit*.
5. **Entry PC and Admin PC:** A-01 turns **green** (available) immediately.
6. Resilience: stop the server (Ctrl+C) → all clients turn amber (*Reconnecting…*) within a second and allocation is paused. Start it again → within a few seconds all clients are **Live** again by themselves, without signing in, and show the current state, including anything that changed meanwhile.

### What has been verified

The release packages were tested on one Windows PC set up like the three computers: the server ran from `SmartPark-Server` against a MongoDB instance with authorization enabled (`smartpark` account with readWrite on `smartpark_db` only), and three separate `SmartPark-Client` copies (ADMIN, ENTRY-GATE-1, EXIT-GATE-1) connected through the PC's **Wi-Fi LAN address**, not localhost. The scenario above passed: A-01 appeared occupied on the admin map in the same second as the entry, and free on the Entry PC in the same second as the exit. Stopping the server switched all clients to *Reconnecting…* in under a second; after a restart they were *Live* again in 2–6 s without signing in, and showed an entry made while they were reconnecting. A wrong database password stops the server at start-up with an authentication error, and the password is masked in the log. Because only one PC was used, the Windows Firewall rule and Wi-Fi traffic between separate machines still have to be checked on site with `Test-Connection.cmd`.

---

## 7. Troubleshooting

| Symptom | Check |
|---|---|
| `Test-Connection.cmd`: cannot open TCP port | Server running? Correct IP (`ipconfig` on the Admin PC)? Same network (not guest Wi-Fi)? Firewall rule added on the Admin PC and network set to *Private*? |
| Port open but `/api/health` fails | Another program uses port 8080 on the Admin PC. Change `server.port` in `application-local.properties`, re-run `open-firewall.cmd <port>`, update the clients. |
| Server stops at start with a MongoDB authentication error | User name/password/`authSource` in `spring.mongodb.uri`; special characters URL-encoded; account created in `smartpark_db`. |
| Server log: "No LAN (private IPv4) address found" | The Admin PC is not connected to the network. |
| Client works on the Admin PC but not on others | Clients on other PCs must use the LAN IP, never `localhost`. |
| Admin PC's IP changed | Set a DHCP reservation, then update `smartpark.server.host` on the Entry and Exit PCs. |
| Sign-in works, map never updates ("Reconnecting…") | A proxy or security software blocks WebSocket upgrades on port 8080; allow `ws://<admin-ip>:8080/ws`. |
