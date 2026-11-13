@echo off
rem ===========================================================================
rem  SmartPark - checks that this PC can reach the server configured in
rem  smartpark-client.properties (REST health check + WebSocket port).
rem ===========================================================================
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = @{}; Get-Content 'smartpark-client.properties' | Where-Object { $_ -match '^\s*smartpark\.server\.(host|port)\s*=' } | ForEach-Object { $k,$v = $_ -split '=',2; $p[$k.Trim()] = $v.Trim() };" ^
  "$h = $p['smartpark.server.host']; $port = $p['smartpark.server.port']; if (-not $port) { $port = 8080 };" ^
  "Write-Host ('Server configured: ' + $h + ':' + $port);" ^
  "$t = Test-NetConnection -ComputerName $h -Port $port -WarningAction SilentlyContinue;" ^
  "if (-not $t.TcpTestSucceeded) { Write-Host 'FAILED: cannot open TCP port' $port 'on' $h -ForegroundColor Red; Write-Host 'Check: server running on the Admin PC, correct IP address, same network, firewall rule (open-firewall.cmd).'; exit 1 };" ^
  "try { $r = Invoke-RestMethod ('http://' + $h + ':' + $port + '/api/health') -TimeoutSec 10; Write-Host ('OK: server ' + $r.status + ', database ' + $r.database + ', server time ' + $r.serverTime) -ForegroundColor Green } catch { if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 503) { Write-Host 'FAILED: the server is running but its database (MongoDB) is unavailable. Check the MongoDB service on the Admin PC.' -ForegroundColor Red } else { Write-Host ('FAILED: port open but /api/health did not answer: ' + $_.Exception.Message) -ForegroundColor Red }; exit 1 }"
pause
