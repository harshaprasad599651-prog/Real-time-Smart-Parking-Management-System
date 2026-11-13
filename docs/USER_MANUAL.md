# SmartPark – User manual

For the people who use SmartPark every day: the **entry operator**, the **exit operator** and the **administrator**. Installation is described in [DEPLOYMENT.md](DEPLOYMENT.md).

---

## 1. Starting and signing in

1. Double-click **SmartPark.cmd** in the SmartPark-Client folder (or its desktop shortcut).
2. The sign-in window shows the server address. Next to it:
   * **Online:** ready.
   * **Unreachable:** the Admin PC or the server is off, or the network is down.
   * **Database unavailable:** the server runs but its database does not; tell the administrator.
3. Enter your **username** and **password** and click **Sign in**.
   * A wrong password shows *Incorrect username or password.*
   * After **5 wrong attempts** the account is locked for **5 minutes**, even for the correct password. The message says how long to wait.
4. The window opens on your role's screen. The left sidebar shows only the screens you may use.

| Role | Opens on | Sidebar |
|---|---|---|
| Entry operator | Vehicle Entry | Vehicle Entry |
| Exit operator | Vehicle Exit | Vehicle Exit |
| Administrator | Overview | Overview, Parked vehicles, Parking history, Slot management, Users, Activity, Terminals, Vehicle Entry, Vehicle Exit |

### Every screen

* **Top right:** the connection indicator and your role.
  * Green **Live:** connected; changes made on other computers appear immediately.
  * Amber **Reconnecting…:** the connection was lost; SmartPark reconnects by itself and reloads the data. Allocation is paused meanwhile.
  * Red **Offline:** no connection.
* **Bottom status bar:** server address, terminal name, time of the last update, and the clock.
* **Sign out:** bottom of the sidebar. Closing the window also signs you out.
* If an administrator deactivates your account or changes your role, SmartPark returns to the sign-in window with a message.

### Slot colours (all maps)

| Colour | Meaning |
|---|---|
| Green, strong border | Available and suitable for the selected vehicle (Entry screen) |
| Light green | Available, but for other vehicle types |
| Red | Occupied (the admin map also shows the vehicle number) |
| Grey, dashed | Disabled (out of use) |
| Blue | The slot you selected |
| Orange | The vehicle you are dealing with (Exit screen) |

---

## 2. Entry operator: registering a vehicle

1. **1. Vehicle registration number:** type the plate, e.g. `WP CAB-1234`. Spaces, hyphens and lower case don't matter.
2. **2. Vehicle category:** e.g. *Car*, *Van*, *Motorcycle*, *Electric car*, *Accessible*. The map now highlights the free slots that suit this vehicle, and the *Available* card shows how many there are.
3. **Notes (optional):** remarks such as "scratch on rear bumper" (up to 200 characters).
4. Click a **highlighted slot** on the map. **3. Selected slot** shows its code and the vehicle details.
5. Click **Confirm allocation**, check the summary and click **Allocate slot …**.
6. The server decides:
   * **Success:** *LAST TICKET ISSUED* shows the ticket ID, the slot turns red on every computer, and the **receipt preview** opens.
   * **Slot taken meanwhile** (another gate was faster): a message appears and the map is refreshed; choose another slot.
   * **Vehicle already parked:** check the number; the vehicle has not left yet.
   * **No answer from the server:** SmartPark checks by itself whether the vehicle was registered and either shows the ticket or tells you that nothing was saved. Never enter the same vehicle twice by hand; the server would refuse it anyway.
7. **Clear** empties the form.

### Receipt preview and printing

* The preview is exactly what will be printed.
* **Printer:** pre-selected from this PC's settings or the Windows default.
* **Paper:**
  * *Automatic* detects the paper of the chosen printer.
  * *Thermal roll 80 mm* and *58 mm* are for receipt printers.
  * *Standard paper (A4 / Letter)* prints the receipt at the top of the page.
* **Print receipt** sends it to the printer. The message *Receipt sent to …* confirms it, and the print is recorded.
* If the printer is missing, offline or the job fails, the dialog says so. Fix the printer or choose another, and click Print again. The parking itself is already saved.
* **Close** returns to the form, ready for the next vehicle.

### Reprinting a receipt

1. In **Reprint receipt**, type the ticket ID printed on the receipt, e.g. `SP-20261113-000026`, and click **Find receipt**.
2. The preview shows the original data, marked **DUPLICATE – REPRINT #n**. Click **Print duplicate**.

Reprinting never creates a new parking session. It also works after the vehicle has left.

---

## 3. Exit operator: confirming a departure

1. **Find parked vehicle:** type the registration number and click **Find** (or press Enter).
2. The details card shows the ticket, slot (highlighted orange on the map), category, entry time, entry operator, time parked and notes.
   * *Not parked:* the vehicle was never registered, or has already left. In that case the screen shows when it left and who confirmed the exit.
3. Click **Confirm vehicle exit** and confirm.
4. *Exit completed – slot … is free* shows the exit time and duration. The slot turns green on every computer.

**Vehicles currently parked** lists everything inside; click a row to load that vehicle. If another gate checks the same vehicle out while you are looking at it, your screen tells you and clears the details.

---

## 4. Administrator

### Overview

* **Cards:** total, available, occupied and disabled slots; vehicles parked; entries today; exits today.
* **Charts:** a pie chart of slot status, and a bar chart of entries and exits per hour today.
* **Live parking map:** occupied slots show the vehicle number. Hover over a slot for its ticket.
* **Live activity:** entries, exits, slot changes, and terminals connecting.

Everything updates automatically; **Refresh** is only needed after a long absence.

### Parked vehicles

Every vehicle inside: number, category, slot, entry time, time parked, ticket ID, entry operator and notes. Use the filter box to search by vehicle, slot or ticket. Double-click a row for the full session.

### Parking history

* Search by **vehicle number**, **entry date from / to** (both days included) and **status** (Active / Completed).
* **Today** and **Reset** are shortcuts. Results show 25 rows per page.
* **View session** (or a double-click) shows the full record, including both operators and the duration.

### Slot management

* **+ Add slot:** code (e.g. `D-01`), type, floor, row, bay, and compatible vehicle categories. Tick *Use every category compatible with the slot type* to take the categories from the type.
* **Edit slot:** change the type, position or categories. The code cannot change.
* **Disable slot / Enable slot:** take a slot out of use, e.g. for repairs. Occupied slots cannot be edited or disabled until the vehicle leaves.

### Users

* **+ New operator:**
  * username;
  * password, at least 8 characters with a letter and a digit, typed twice;
  * role.
* **Assign role:** changes the role. The user is signed out everywhere and signs in again with the new role.
* **Deactivate / Activate:** a deactivated user is signed out at once and cannot sign in.
* You cannot deactivate or demote your own account. The last active administrator cannot be removed either.

### Activity

* The audit log, filtered by role, action and user. Actions include entries, exits, receipts printed and reprinted, sign-ins and failed sign-ins, lockouts, refused operations, and slot, category and user changes.
* New entries appear at the top as they happen.
* The bar chart shows entries and exits per operator.

### Terminals

Which operators are connected right now, from which terminal and address, and since when.

---

## 5. Quick troubleshooting

| You see | Do this |
|---|---|
| Sign-in says *Unreachable* | Check the network; ask the administrator whether the server is running; run **Test-Connection.cmd** |
| *Database unavailable* | The administrator must start the MongoDB service on the Admin PC |
| Amber *Reconnecting…* for a long time | Same as above; SmartPark keeps trying and continues automatically once the server is back |
| *Too many failed sign-in attempts* | Wait the stated minutes, or ask the administrator |
| Receipt does not print | Check the printer (power, paper, cable); choose another printer in the preview; or print later with **Reprint receipt** |
| A slot is red but the car has left | The exit was not confirmed: search the vehicle at the exit screen and confirm the exit |
