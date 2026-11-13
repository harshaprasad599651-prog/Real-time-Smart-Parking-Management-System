@echo off
rem ===========================================================================
rem  SmartPark desktop client (Entry, Exit and Admin PCs)
rem
rem  Settings are read from smartpark-client.properties in THIS folder:
rem    smartpark.server.host  = LAN IP address of the Admin PC (e.g. 192.168.1.10)
rem    smartpark.server.port  = 8080
rem    smartpark.terminal.name= ENTRY-GATE-1 / EXIT-GATE-1 / ADMIN
rem  Change them with Notepad and restart the client. No recompiling needed.
rem  Java is included (runtime folder); nothing else has to be installed.
rem ===========================================================================
setlocal
cd /d "%~dp0"

if not exist "runtime\bin\javaw.exe" (
    echo The runtime folder is missing. Copy the complete SmartPark-Client folder.
    pause
    exit /b 1
)
if not exist "smartpark-client.properties" (
    echo smartpark-client.properties is missing in %~dp0
    pause
    exit /b 1
)

start "SmartPark" "runtime\bin\javaw.exe" --enable-native-access=javafx.graphics -m com.smartpark.client/com.smartpark.client.app.SmartParkClientApp
