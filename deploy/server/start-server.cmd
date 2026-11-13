@echo off
rem ===========================================================================
rem  SmartPark server - Admin PC
rem
rem  Starts the Spring Boot backend from this folder. Settings (MongoDB login,
rem  passwords, listen address) are read from application-local.properties in
rem  this folder. Copy application-local.properties.example to create it.
rem
rem  The server prints the LAN address the Entry and Exit PCs must use.
rem  Stop the server with Ctrl+C (or close this window).
rem ===========================================================================
setlocal
cd /d "%~dp0"
title SmartPark Server

where java >nul 2>nul
if errorlevel 1 (
    echo Java was not found. Install a Java 21 or newer runtime ^(e.g. Eclipse Temurin 21^) and try again.
    pause
    exit /b 1
)

if not exist "application-local.properties" (
    echo WARNING: application-local.properties not found - using built-in defaults
    echo          ^(MongoDB without a password, sample passwords^). See DEPLOYMENT.md.
    echo.
)

java -jar smartpark-server.jar
echo.
echo SmartPark server stopped.
pause
