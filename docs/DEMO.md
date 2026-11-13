# SmartPark – University demonstration (three computers)

A 15–20 minute live demonstration showing the network programming core (client–server over a LAN, REST and WebSocket, concurrency control, fault tolerance) and the complete parking workflow.

## Roles and setup

| Computer | Presenter | Runs | Signed in as | Window placement |
|---|---|---|---|---|
| **PC 1 – Admin** (projector) | Presenter A | `start-server.cmd` (server console) + SmartPark client | `admin` | Overview, maximised |
| **PC 2 – Entry gate** | Presenter B | SmartPark client | `entry1` | Vehicle Entry |
| **PC 3 – Exit gate** | Presenter C | SmartPark client | `exit1` | Vehicle Exit |

If only one presenter is available, place PC 2 and PC 3 next to the projector so the audience can see all three screens.

**Props:**
* A printer at the Entry PC: a USB thermal receipt printer if available, otherwise an office printer or *Microsoft Print to PDF*.
* Two "vehicles" written on cards: `WP CAB-1234` (car) and `NB-5678` (car).

### Step 0 – before the audience arrives (15 min)

1. All three PCs on the **same Wi-Fi/LAN** (not guest Wi-Fi), network profile **Private**.
2. PC 1:
   * MongoDB service running.
   * Run `open-firewall.cmd` as Administrator (once).
   * Start `start-server.cmd` and note the printed line `LAN clients: smartpark.server.host=<IP>`.
3. PC 2 and PC 3: put that IP in `smartpark-client.properties` and run **Test-Connection.cmd**. It must print `OK: server UP, database UP`.
4. Use a **clean demo database** so the numbers are easy to follow. In PC 1's `application-local.properties`:
   ```properties
   spring.mongodb.database=smartpark_demo
   ```
   On the first start the server creates the 30 sample slots and the accounts `admin`, `entry1` and `exit1`.
5. Sign in on all three PCs. Every top bar must show green **Live**. On PC 1, open **Terminals**: three connections from three different IP addresses. Then go back to **Overview**.
6. Rehearse once, then make sure A-01 is free again (exit the rehearsal vehicles).

---

## Script

### 1. Introduction (2 min) – PC 1 on the projector

* The problem: gates that don't share state send two cars to one space and keep no records.
* The architecture: one server and database on the Admin PC; three JavaFX clients; **REST** for commands, **WebSocket** for live updates; the **database is the single source of truth**.
* Show the server console: the LAN address line and *MongoDB indexes verified*. Optionally open `http://<IP>:8080/swagger-ui.html` to show the 31 documented API operations.

### 2. Vehicle entry (3 min) – PC 2

1. Type `wp cab-1234` (lower case, with a space, on purpose), choose **Car**.
   → The compatible free slots light up; the *Available* card says *20 fit Car*.
2. Click **A-01** → *Confirm allocation* → **Allocate slot A-01**.
3. **Point at PC 1 (projector):** A-01 has turned **red with `WPCAB1234`** on the live map. The *Occupied* card went up, and *Live activity* shows the entry. Nobody pressed refresh: the server pushed the change over the WebSocket.
4. Back on PC 2: the ticket ID (`SP-yyyyMMdd-000001`) and the **receipt preview**. Click **Print receipt** and hold up the printed ticket. Point out:
   * the entry time comes from the **server clock**;
   * the number was normalised to `WPCAB1234`;
   * the receipt shows ticket, vehicle, category, slot, date, time and operator.

### 3. The same slot from two places (3 min) – PC 2 + PC 1

*Shows the concurrency control.*

1. PC 2: type `NB-5678`, **Car**, click **A-02**, and click *Confirm allocation*, but **leave the confirmation dialog open**.
2. PC 1 (admin): sidebar **Vehicle Entry**, type `XYZ-999`, **Car**, click **A-02**, confirm → allocated.
3. PC 2: A-02 has already turned red on the map behind the dialog. Click **Allocate slot A-02** now → *"Slot A-02 is no longer available…"*. The server refused it and the map is up to date.
4. Explain:
   * the server claims a slot with **one atomic database operation** that only succeeds while the slot is AVAILABLE;
   * **unique indexes** in MongoDB are the second line of defence;
   * an automated test fires 20 simultaneous requests for one slot and exactly 1 succeeds ([TESTING.md](TESTING.md) §4).
5. PC 2: choose **A-03** for `NB-5678` → allocated.

### 4. Vehicle exit (2 min) – PC 3

1. Type `WP CAB-1234` → **Find** → session details: ticket, slot A-01 highlighted orange, time parked.
2. **Confirm vehicle exit** → *Exit completed – slot A-01 is free*, with duration.
3. **Point at PC 2 and PC 1:** A-01 is **green** again on both, instantly.
4. Search `WP CAB-1234` again → *"WPCAB1234 is not parked now: ticket … was already completed at … by exit1 (slot A-01)"*. A vehicle cannot leave twice.

### 5. Administration (3 min) – PC 1

* **Parking history:** search `WPCAB1234` → **View session** shows entry and exit, both operators and the duration.
* **Parked vehicles:** `NB5678` and `XYZ999`.
* **Activity:** every action with operator and time. Filter by *Entry operator*.
* **Users:** create `entry2` (Entry operator) → it appears in *Activity* immediately. Optionally show that the server refuses an entry operator who tries an admin function (403, logged as `ACCESS_DENIED`).
* **Slot management:** disable **C-08** → it turns grey on PC 2 and PC 3 at once.

### 6. Reprint (1 min) – PC 2

**Reprint receipt** → type the ticket ID from the printed receipt → **Find receipt** → the preview is marked *DUPLICATE – REPRINT #1* → print. No new session is created (*Parked vehicles* is unchanged).

### 7. Network failure and recovery (3 min)

1. **Unplug PC 3's network cable / switch off its Wi-Fi.** PC 3's indicator turns amber, *Reconnecting…*.
2. Meanwhile, on PC 3 … nothing can be done. On **PC 1**, register the exit of `NB-5678` (sidebar **Vehicle Exit**). A-03 turns green on PC 1 and PC 2.
3. **Reconnect PC 3:** within seconds it is **Live** again, and A-03 is green. PC 3 received the missed events and reloaded its data from the server.
4. Optional, more dramatic: on PC 1 press **Ctrl+C** in the server console.
   * All three clients show *Reconnecting…*, and allocation is paused, so no wrong data can be created.
   * Run `start-server.cmd` again. All clients return to **Live** by themselves (about 20–30 s, mostly server start-up), still signed in, showing the current state.

### 8. Close (1 min)

Summarise:
* REST commands plus WebSocket push over one port.
* Server-side authority and atomic allocation.
* Persistent records and printed tickets.
* Role-based security.
* Recovery from network and server failures.

Mention the 207 automated tests and the 61-check API system test.

---

## Likely questions

| Question | Short answer |
|---|---|
| Why WebSocket and not polling? | Push is instant and uses no bandwidth when nothing changes; REST still carries every command so each one gets a definite answer. |
| What if two gates click the same slot in the same millisecond? | MongoDB applies the conditional update atomically, so only one matches. The second gets 409 and its map reloads. |
| What if the server crashes between claiming the slot and saving the session? | On a replica set both writes are one transaction. On a standalone server a failed insert is compensated, and a reconciliation job at start-up frees any slot left without an active session. |
| How are clients kept consistent after a disconnection? | Every event has a sequence number. On reconnect the client sends the last number it processed: missed events are replayed in order, or after a server restart it reloads everything. Duplicates are ignored. |
| Where are passwords stored? | Only as BCrypt hashes. Tokens are stored only as SHA-256 hashes. MongoDB itself is password-protected and not reachable from the LAN. |
| Can an entry operator call admin functions directly? | No. Roles are checked on the server for every request (403, logged). The client only hides what you may not use. |
| Can the server address change without recompiling? | Yes: `smartpark-client.properties` next to the client. |

## If something goes wrong during the demo

| Problem | Recovery |
|---|---|
| A client shows *Unreachable* at sign-in | Run `Test-Connection.cmd`; check the IP (`ipconfig` on PC 1) and the firewall rule. As a fallback, run all three clients on PC 1 (one folder per terminal, `smartpark.server.host=localhost`) and continue |
| Printer fails | Choose *Microsoft Print to PDF* in the preview and show the PDF; or skip printing and use **Reprint receipt** later |
| A demo vehicle is still parked from the rehearsal | Exit it on PC 3 before starting, or use another vehicle number |
| *Too many failed sign-in attempts* | Wait 5 minutes or restart the server (lockouts are kept in memory) |
