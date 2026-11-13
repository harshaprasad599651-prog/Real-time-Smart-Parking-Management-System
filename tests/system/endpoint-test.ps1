# SmartPark - system test of every REST operation (31 operations, 61 checks) against a
# running server. Each operation is called with the right role, and the important refusals
# (no token, wrong role, invalid input, conflicts) are checked too.
#
# The server must start on an EMPTY database so the sample data (30 slots, admin/entry1/exit1
# with the default passwords) is created:
#   $env:SMARTPARK_MONGODB_DATABASE = "smartpark_systest"; java -jar smartpark-server\target\smartpark-server.jar
# Then, in another PowerShell window:
#   powershell -ExecutionPolicy Bypass -File tests\system\endpoint-test.ps1 -Base http://localhost:8080 -Out results.csv
# Drop the smartpark_systest database afterwards (e.g. in MongoDB Compass).
param([string]$Base = "http://localhost:8080", [string]$Out = "endpoint-results.csv")
$ErrorActionPreference = "Stop"
$results = New-Object System.Collections.Generic.List[object]

function Call([string]$method, [string]$path, $token = $null, $body = $null) {
  $req = [System.Net.HttpWebRequest]::Create("$Base$path")
  $req.Method = $method; $req.Timeout = 15000; $req.Accept = "application/json"
  if ($token) { $req.Headers.Add("Authorization", "Bearer $token") }
  if ($body -ne $null) {
    $req.ContentType = "application/json"
    $bytes = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 5 -Compress))
    $s = $req.GetRequestStream(); $s.Write($bytes, 0, $bytes.Length); $s.Close()
  } elseif ($method -eq "POST" -or $method -eq "PUT") { $req.ContentLength = 0 }
  try { $resp = $req.GetResponse() } catch [System.Net.WebException] { $resp = $_.Exception.Response; if (-not $resp) { throw } }
  $text = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd(); $code = [int]$resp.StatusCode; $resp.Close()
  $json = $null; if ($text) { try { $json = $text | ConvertFrom-Json } catch {} }
  return [pscustomobject]@{ Status = $code; Json = $json; Text = $text }
}

function Check([string]$id, [string]$op, [string]$case, $r, [int]$expected, [string]$code = $null, [scriptblock]$extra = $null) {
  $ok = $r.Status -eq $expected
  if ($ok -and $code) { $ok = $r.Json.code -eq $code }
  $note = ""
  if ($ok -and $extra) { try { $ok = [bool](& $extra $r); if (-not $ok) { $note = "content check failed" } } catch { $ok = $false; $note = $_.Exception.Message } }
  $got = "$($r.Status)" + $(if ($r.Json -and $r.Json.code) { " " + $r.Json.code } else { "" })
  $results.Add([pscustomobject]@{ Id = $id; Operation = $op; Case = $case; Expected = "$expected" + $(if ($code) { " $code" } else { "" }); Actual = $got; Result = $(if ($ok) { "PASS" } else { "FAIL" }); Note = $note })
}

function Login($u, $p) { (Call POST "/api/auth/login" $null @{ username = $u; password = $p; terminal = "SYSTEM-TEST" }).Json.token }

# ---------------------------------------------------------------- auth & health
$r = Call GET "/api/health"; Check "E01" "GET /api/health" "public, database up" $r 200 -extra { param($x) $x.Json.status -eq "UP" -and $x.Json.database -eq "UP" }
$r = Call POST "/api/auth/login" $null @{ username = "admin"; password = "Admin@123"; terminal = "SYSTEM-TEST" }
Check "E02" "POST /api/auth/login" "valid credentials -> token" $r 200 -extra { param($x) $x.Json.token.Length -ge 40 -and $x.Json.user.role -eq "ADMIN" }
$admin = $r.Json.token
$r = Call POST "/api/auth/login" $null @{ username = "admin"; password = "wrong-1"; terminal = "SYSTEM-TEST" }; Check "E03" "POST /api/auth/login" "wrong password" $r 401 "INVALID_CREDENTIALS"
$r = Call POST "/api/auth/login" $null @{ username = ""; password = "" }; Check "E04" "POST /api/auth/login" "blank fields" $r 400 "VALIDATION_FAILED"
$entry = Login "entry1" "Entry@123"; $exit = Login "exit1" "Exit@123"
$r = Call GET "/api/auth/me" $entry; Check "E05" "GET /api/auth/me" "entry operator" $r 200 -extra { param($x) $x.Json.username -eq "entry1" }
$r = Call GET "/api/auth/me"; Check "E06" "GET /api/auth/me" "no token" $r 401 "UNAUTHENTICATED"

# ---------------------------------------------------------------- categories
$r = Call GET "/api/categories" $entry; Check "E07" "GET /api/categories" "signed in" $r 200 -extra { param($x) @($x.Json).Count -ge 5 }
$r = Call GET "/api/categories/car" $entry; Check "E08" "GET /api/categories/{name}" "case-insensitive" $r 200 -extra { param($x) $x.Json.categoryName -eq "CAR" }
$r = Call GET "/api/categories/HELICOPTER" $entry; Check "E09" "GET /api/categories/{name}" "unknown" $r 404 "NOT_FOUND"
$r = Call POST "/api/categories" $admin @{ categoryName = "Light truck"; description = "Small trucks"; compatibleSlotTypes = @("LARGE") }
Check "E10" "POST /api/categories" "admin creates" $r 201 -extra { param($x) $x.Json.categoryName -eq "LIGHT_TRUCK" }
$r = Call POST "/api/categories" $entry @{ categoryName = "Bus"; compatibleSlotTypes = @("LARGE") }; Check "E11" "POST /api/categories" "entry operator" $r 403 "OPERATOR_NOT_AUTHORIZED"
$r = Call POST "/api/categories" $admin @{ categoryName = "Light truck"; compatibleSlotTypes = @("LARGE") }; Check "E12" "POST /api/categories" "duplicate" $r 409 "DUPLICATE_RESOURCE"
$r = Call PUT "/api/categories/LIGHT_TRUCK" $admin @{ description = "Trucks up to 3.5 t"; compatibleSlotTypes = @("LARGE") }
Check "E13" "PUT /api/categories/{name}" "admin updates" $r 200 -extra { param($x) $x.Json.description -eq "Trucks up to 3.5 t" }

# ---------------------------------------------------------------- slots
$r = Call GET "/api/slots" $entry; Check "E14" "GET /api/slots" "map order" $r 200 -extra { param($x) @($x.Json).Count -eq 30 }
$r = Call GET "/api/slots"; Check "E15" "GET /api/slots" "no token" $r 401 "UNAUTHENTICATED"
$r = Call GET "/api/slots/available?category=CAR" $entry; Check "E16" "GET /api/slots/available" "for CAR" $r 200 -extra { param($x) @($x.Json | Where-Object { $_.status -ne "AVAILABLE" }).Count -eq 0 }
$r = Call GET "/api/slots/a-01" $entry; Check "E17" "GET /api/slots/{code}" "case-insensitive" $r 200 -extra { param($x) $x.Json.slotCode -eq "A-01" }
$r = Call POST "/api/slots" $admin @{ slotCode = "D-01"; slotType = "STANDARD"; floor = 1; row = "D"; column = 1 }
Check "E18" "POST /api/slots" "admin adds (categories derived)" $r 201 -extra { param($x) $x.Json.supportedVehicleCategories -contains "CAR" }
$r = Call POST "/api/slots" $admin @{ slotCode = "bad code!"; slotType = "STANDARD"; floor = 1; row = "D"; column = 2 }; Check "E19" "POST /api/slots" "invalid code" $r 400 "VALIDATION_FAILED"
$r = Call PUT "/api/slots/D-01" $admin @{ slotType = "LARGE"; floor = 1; row = "D"; column = 1 }
Check "E20" "PUT /api/slots/{code}" "admin edits" $r 200 -extra { param($x) $x.Json.slotType -eq "LARGE" }
$r = Call POST "/api/slots/D-01/disable" $admin; Check "E21" "POST /api/slots/{code}/disable" "available -> disabled" $r 200 -extra { param($x) $x.Json.status -eq "DISABLED" }
$r = Call POST "/api/slots/D-01/enable" $admin; Check "E22" "POST /api/slots/{code}/enable" "disabled -> available" $r 200 -extra { param($x) $x.Json.status -eq "AVAILABLE" }
$r = Call POST "/api/slots/D-01/disable" $exit; Check "E23" "POST /api/slots/{code}/disable" "exit operator" $r 403 "OPERATOR_NOT_AUTHORIZED"

# ---------------------------------------------------------------- entry
$r = Call POST "/api/parking/entry" $entry @{ vehicleNumber = "wp cab-1234"; categoryName = "car"; slotCode = "a-01"; notes = "System test" }
Check "E24" "POST /api/parking/entry" "valid entry" $r 201 -extra { param($x) $x.Json.ticketId -match '^SP-\d{8}-\d{6}$' -and $x.Json.vehicleNumber -eq "WPCAB1234" -and $x.Json.status -eq "ACTIVE" }
$ticket = $r.Json.ticketId
$r = Call POST "/api/parking/entry" $entry @{ vehicleNumber = "NB-5678"; categoryName = "CAR"; slotCode = "A-01" }; Check "E25" "POST /api/parking/entry" "slot occupied" $r 409 "SLOT_UNAVAILABLE"
$r = Call POST "/api/parking/entry" $entry @{ vehicleNumber = "WPCAB1234"; categoryName = "CAR"; slotCode = "A-02" }; Check "E26" "POST /api/parking/entry" "vehicle already parked" $r 409 "VEHICLE_ALREADY_PARKED"
$r = Call POST "/api/parking/entry" $entry @{ vehicleNumber = "BIKE-01"; categoryName = "MOTORCYCLE"; slotCode = "A-03" }; Check "E27" "POST /api/parking/entry" "incompatible slot" $r 422 "SLOT_NOT_COMPATIBLE"
$r = Call POST "/api/parking/entry" $entry @{ vehicleNumber = "#!"; categoryName = "CAR"; slotCode = "A-03" }; Check "E28" "POST /api/parking/entry" "invalid vehicle number" $r 400 "VALIDATION_FAILED"
$r = Call POST "/api/parking/entry" $exit @{ vehicleNumber = "EXIT-OP-1"; categoryName = "CAR"; slotCode = "A-03" }; Check "E29" "POST /api/parking/entry" "exit operator" $r 403 "OPERATOR_NOT_AUTHORIZED"

# ---------------------------------------------------------------- receipts
$r = Call GET "/api/parking/receipts/$ticket" $entry; Check "E30" "GET /api/parking/receipts/{ticket}" "stored ticket" $r 200 -extra { param($x) $x.Json.parkingSlotCode -eq "A-01" -and $x.Json.printCount -eq 0 }
$r = Call POST "/api/parking/receipts/$ticket/prints" $entry @{ printerName = "System test printer" }; Check "E31" "POST /api/parking/receipts/{ticket}/prints" "first print" $r 200 -extra { param($x) $x.Json.printCount -eq 1 }
$r = Call POST "/api/parking/receipts/$($ticket.ToLower())/prints" $admin; Check "E32" "POST /api/parking/receipts/{ticket}/prints" "reprint (count 2)" $r 200 -extra { param($x) $x.Json.printCount -eq 2 }
$r = Call GET "/api/parking/receipts/SP-19990101-000001" $entry; Check "E33" "GET /api/parking/receipts/{ticket}" "unknown ticket" $r 404 "NOT_FOUND"
$r = Call GET "/api/parking/receipts/$ticket" $exit; Check "E34" "GET /api/parking/receipts/{ticket}" "exit operator" $r 403 "OPERATOR_NOT_AUTHORIZED"

# ---------------------------------------------------------------- monitoring
$r = Call GET "/api/parking/sessions/active" $entry; Check "E35" "GET /api/parking/sessions/active" "one parked" $r 200 -extra { param($x) @($x.Json).Count -eq 1 }
$r = Call GET "/api/parking/sessions/$ticket" $entry; Check "E36" "GET /api/parking/sessions/{ticket}" "by ticket" $r 200 -extra { param($x) $x.Json.notes -eq "System test" }
$r = Call GET "/api/statistics" $entry; Check "E37" "GET /api/statistics" "occupancy" $r 200 -extra { param($x) $x.Json.occupiedSlots -eq 1 -and $x.Json.totalSlots -eq 31 }

# ---------------------------------------------------------------- exit
$r = Call GET "/api/parking/active/wp%20cab-1234" $exit; Check "E38" "GET /api/parking/active/{vehicle}" "found, normalized" $r 200 -extra { param($x) $x.Json.slot.slotCode -eq "A-01" }
$r = Call GET "/api/parking/active/NOTPARKED1" $exit; Check "E39" "GET /api/parking/active/{vehicle}" "not parked" $r 404 "NO_ACTIVE_SESSION"
$r = Call GET "/api/parking/active/WPCAB1234" $entry; Check "E40" "GET /api/parking/active/{vehicle}" "entry operator" $r 403 "OPERATOR_NOT_AUTHORIZED"
$r = Call POST "/api/parking/exit" $entry @{ vehicleNumber = "WPCAB1234" }; Check "E41" "POST /api/parking/exit" "entry operator" $r 403 "OPERATOR_NOT_AUTHORIZED"
$r = Call POST "/api/parking/exit" $exit @{ vehicleNumber = "WPCAB1234" }
Check "E42" "POST /api/parking/exit" "valid exit" $r 200 -extra { param($x) $x.Json.status -eq "COMPLETED" -and $x.Json.exitOperator -eq "exit1" -and $x.Json.durationMinutes -ge 0 }
$r = Call POST "/api/parking/exit" $exit @{ vehicleNumber = "WPCAB1234" }; Check "E43" "POST /api/parking/exit" "second exit" $r 404 "NO_ACTIVE_SESSION"
$r = Call GET "/api/slots/A-01" $entry; Check "E44" "GET /api/slots/{code}" "slot free after exit" $r 200 -extra { param($x) $x.Json.status -eq "AVAILABLE" -and -not $x.Json.currentTicketId }
$r = Call GET "/api/parking/sessions?vehicleNumber=WPCAB1234&status=COMPLETED" $exit; Check "E45" "GET /api/parking/sessions" "history search" $r 200 -extra { param($x) $x.Json.totalElements -eq 1 }
$r = Call GET "/api/parking/sessions?size=500" $exit; Check "E46" "GET /api/parking/sessions" "page size too large" $r 400 "VALIDATION_FAILED"
$r = Call GET "/api/parking/sessions" $entry; Check "E47" "GET /api/parking/sessions" "entry operator" $r 403 "OPERATOR_NOT_AUTHORIZED"

# ---------------------------------------------------------------- users, activity, realtime
$r = Call GET "/api/users" $admin; Check "E48" "GET /api/users" "admin, no hashes" $r 200 -extra { param($x) -not ($x.Text -match "password") }
$r = Call POST "/api/users" $admin @{ username = "gate2"; password = "Gate2pass9"; role = "ENTRY_OPERATOR" }; Check "E49" "POST /api/users" "create operator" $r 201
$r = Call POST "/api/users" $admin @{ username = "gate3"; password = "short"; role = "ENTRY_OPERATOR" }; Check "E50" "POST /api/users" "weak password" $r 400 "VALIDATION_FAILED"
$r = Call PUT "/api/users/gate2/role" $admin @{ role = "EXIT_OPERATOR" }; Check "E51" "PUT /api/users/{user}/role" "assign role" $r 200 -extra { param($x) $x.Json.role -eq "EXIT_OPERATOR" }
$r = Call POST "/api/users/gate2/deactivate" $admin; Check "E52" "POST /api/users/{user}/deactivate" "deactivate" $r 200 -extra { param($x) -not $x.Json.active }
$r = Call POST "/api/users/gate2/activate" $admin; Check "E53" "POST /api/users/{user}/activate" "activate" $r 200 -extra { param($x) $x.Json.active }
$r = Call POST "/api/users/admin/deactivate" $admin; Check "E54" "POST /api/users/{user}/deactivate" "own account" $r 409 "INVALID_STATE"
$r = Call GET "/api/users" $entry; Check "E55" "GET /api/users" "entry operator" $r 403 "OPERATOR_NOT_AUTHORIZED"
$r = Call GET "/api/activity?action=VEHICLE_ENTRY&limit=10" $admin; Check "E56" "GET /api/activity" "filter by action" $r 200 -extra { param($x) @($x.Json).Count -ge 1 }
$r = Call GET "/api/activity" $exit; Check "E57" "GET /api/activity" "exit operator" $r 403 "OPERATOR_NOT_AUTHORIZED"
$r = Call GET "/api/realtime/status" $admin; Check "E58" "GET /api/realtime/status" "admin" $r 200
$r = Call GET "/api/realtime/status" $entry; Check "E59" "GET /api/realtime/status" "entry operator" $r 403 "OPERATOR_NOT_AUTHORIZED"

# ---------------------------------------------------------------- logout
$r = Call POST "/api/auth/logout" $entry; Check "E60" "POST /api/auth/logout" "sign out" $r 204
$r = Call GET "/api/auth/me" $entry; Check "E61" "GET /api/auth/me" "token revoked after logout" $r 401 "UNAUTHENTICATED"

$results | Export-Csv -NoTypeInformation -Encoding UTF8 $Out
$pass = @($results | Where-Object Result -eq "PASS").Count
"$pass / $($results.Count) passed"
$results | Where-Object Result -ne "PASS" | Format-Table -AutoSize | Out-String -Width 250
