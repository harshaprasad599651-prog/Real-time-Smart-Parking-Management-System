@echo off
rem ===========================================================================
rem  SmartPark - Windows Firewall rule for the Admin PC  (run as Administrator)
rem
rem  Allows the Entry and Exit PCs to reach the SmartPark server on TCP 8080
rem  (REST API and WebSocket share this one port). Only "Private" networks,
rem  and only from the local subnet. MongoDB (27017) is NOT opened: only the
rem  server on this PC talks to the database.
rem
rem  Usage:  open-firewall.cmd          (port 8080)
rem          open-firewall.cmd 9090     (another port, if server.port was changed)
rem  Remove: netsh advfirewall firewall delete rule name="SmartPark Server"
rem ===========================================================================
setlocal
set PORT=%~1
if "%PORT%"=="" set PORT=8080

net session >nul 2>nul
if errorlevel 1 (
    echo Please run this script as Administrator ^(right-click, "Run as administrator"^).
    pause
    exit /b 1
)

netsh advfirewall firewall delete rule name="SmartPark Server" >nul 2>nul
netsh advfirewall firewall add rule name="SmartPark Server" dir=in action=allow protocol=TCP localport=%PORT% profile=private remoteip=localsubnet description="SmartPark REST API and WebSocket"
if errorlevel 1 (
    echo Could not add the firewall rule.
    pause
    exit /b 1
)
echo.
echo Inbound TCP %PORT% is now allowed from the local network ^(Private profile^).
echo Make sure this PC's network is set to "Private" in Windows Settings ^> Network ^& Internet.
pause
