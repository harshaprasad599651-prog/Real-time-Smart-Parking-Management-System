@echo off
rem ===========================================================================
rem  SmartPark - builds the packages to copy to the three PCs.
rem
rem    dist\SmartPark-Server\   -> Admin PC   (server jar, start script, firewall
rem                                            script, MongoDB account script)
rem    dist\SmartPark-Client\   -> every PC   (client + its own Java runtime)
rem    dist\SmartPark-Server.zip, dist\SmartPark-Client.zip
rem
rem  Run on the development PC from any folder:  deploy\build-release.cmd
rem  Add  skiptests  to skip the automated tests.
rem ===========================================================================
setlocal
cd /d "%~dp0.."
set ROOT=%CD%
set DIST=%ROOT%\dist

set TESTS=
if /i "%~1"=="skiptests" set TESTS=-DskipTests

echo [1/3] Building and testing server and client...
call "%ROOT%\mvnw.cmd" -B -q clean verify %TESTS%
if errorlevel 1 goto failed

echo [2/3] Creating the client runtime image (Java + JavaFX + client)...
call "%ROOT%\mvnw.cmd" -B -q -pl smartpark-client javafx:jlink
if errorlevel 1 goto failed

echo [3/3] Assembling %DIST% ...
if exist "%DIST%" rmdir /s /q "%DIST%"
mkdir "%DIST%\SmartPark-Server\mongodb" "%DIST%\SmartPark-Client"

copy /y "%ROOT%\smartpark-server\target\smartpark-server.jar" "%DIST%\SmartPark-Server\" >nul
copy /y "%ROOT%\smartpark-server\application-local.properties.example" "%DIST%\SmartPark-Server\" >nul
copy /y "%ROOT%\deploy\server\*.cmd" "%DIST%\SmartPark-Server\" >nul
copy /y "%ROOT%\deploy\server\mongodb\*" "%DIST%\SmartPark-Server\mongodb\" >nul
copy /y "%ROOT%\docs\DEPLOYMENT.md" "%DIST%\SmartPark-Server\" >nul

xcopy /e /i /q /y "%ROOT%\smartpark-client\target\smartpark-client-image" "%DIST%\SmartPark-Client\runtime" >nul
copy /y "%ROOT%\deploy\client\*" "%DIST%\SmartPark-Client\" >nul
copy /y "%ROOT%\smartpark-client\src\main\resources\receipt-template.properties" "%DIST%\SmartPark-Client\" >nul
copy /y "%ROOT%\docs\DEPLOYMENT.md" "%DIST%\SmartPark-Client\" >nul

powershell -NoProfile -Command "Compress-Archive -Force -Path '%DIST%\SmartPark-Server' -DestinationPath '%DIST%\SmartPark-Server.zip'; Compress-Archive -Force -Path '%DIST%\SmartPark-Client' -DestinationPath '%DIST%\SmartPark-Client.zip'"
if errorlevel 1 goto failed

echo.
echo Done:
echo   %DIST%\SmartPark-Server.zip  -^> Admin PC
echo   %DIST%\SmartPark-Client.zip  -^> Admin, Entry and Exit PCs
exit /b 0

:failed
echo.
echo BUILD FAILED - see the messages above.
exit /b 1
