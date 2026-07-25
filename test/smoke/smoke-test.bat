@echo off
REM ============================================================
REM MR Board System - Smoke Test Script (Windows)
REM ============================================================
setlocal enabledelayedexpansion

set BASE_URL=%1
if "%BASE_URL%"=="" set BASE_URL=http://localhost:8080
set ADMIN_USER=%2
if "%ADMIN_USER%"=="" set ADMIN_USER=admin
set ADMIN_PASS=%3
if "%ADMIN_PASS%"=="" set ADMIN_PASS=Admin@123

set PASS=0
set FAIL=0

echo.
echo =============================================
echo  MR Board - Smoke Test
echo  Target: %BASE_URL%
echo =============================================

REM --- Health ---
echo.
echo [1] Health Check
curl -s -o NUL -w "HTTP %%{http_code}" "%BASE_URL%/api" --connect-timeout 10 2>NUL
echo.

REM --- Login ---
echo.
echo [2] Login
for /f "tokens=*" %%i in ('curl -s -X POST "%BASE_URL%/api/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"%ADMIN_USER%\",\"password\":\"%ADMIN_PASS%\"}" --connect-timeout 10 2^>NUL') do set LOGIN_RESP=%%i
echo !LOGIN_RESP! | findstr "accessToken" >NUL
if %ERRORLEVEL% EQU 0 (echo [PASS] Login successful) else (echo [FAIL] Login failed)

REM Extract token
for /f "tokens=2 delims=:" %%a in ('echo !LOGIN_RESP! ^| findstr "accessToken"') do for /f "tokens=1 delims=," %%b in ("%%a") do set TOKEN=%%~b
set TOKEN=!TOKEN:"=!
set AUTH=Authorization: Bearer !TOKEN!

REM --- Board ---
echo.
echo [3] Board Dashboard
curl -s "%BASE_URL%/api/board" -H "!AUTH!" --connect-timeout 10 | findstr "code" >NUL
if %ERRORLEVEL% EQU 0 (echo [PASS] GET /api/board) else (echo [FAIL] GET /api/board)

echo [4] Board Stats
curl -s "%BASE_URL%/api/board/stats" -H "!AUTH!" --connect-timeout 10 | findstr "code" >NUL
if %ERRORLEVEL% EQU 0 (echo [PASS] GET /api/board/stats) else (echo [FAIL] GET /api/board/stats)

echo [5] Board Projects
curl -s "%BASE_URL%/api/board/projects" -H "!AUTH!" --connect-timeout 10 | findstr "code" >NUL
if %ERRORLEVEL% EQU 0 (echo [PASS] GET /api/board/projects) else (echo [FAIL] GET /api/board/projects)

REM --- Projects ---
echo.
echo [6] Git Sources
curl -s "%BASE_URL%/api/git-sources" -H "!AUTH!" --connect-timeout 10 | findstr "code" >NUL
if %ERRORLEVEL% EQU 0 (echo [PASS] GET /api/git-sources) else (echo [FAIL] GET /api/git-sources)

echo [7] Projects
curl -s "%BASE_URL%/api/projects?page=1&size=10" -H "!AUTH!" --connect-timeout 10 | findstr "code" >NUL
if %ERRORLEVEL% EQU 0 (echo [PASS] GET /api/projects) else (echo [FAIL] GET /api/projects)

REM --- Reports ---
echo.
echo [8] Reports Summary
curl -s "%BASE_URL%/api/reports/summary" -H "!AUTH!" --connect-timeout 10 | findstr "code" >NUL
if %ERRORLEVEL% EQU 0 (echo [PASS] GET /api/reports/summary) else (echo [FAIL] GET /api/reports/summary)

REM --- Users ---
echo.
echo [9] User Management
curl -s "%BASE_URL%/api/admin/users?page=1&size=10" -H "!AUTH!" --connect-timeout 10 | findstr "code" >NUL
if %ERRORLEVEL% EQU 0 (echo [PASS] GET /api/admin/users) else (echo [FAIL] GET /api/admin/users)

REM --- Sync Logs ---
echo.
echo [10] Sync Logs
curl -s "%BASE_URL%/api/admin/sync-logs?page=1&size=10" -H "!AUTH!" --connect-timeout 10 | findstr "code" >NUL
if %ERRORLEVEL% EQU 0 (echo [PASS] GET /api/admin/sync-logs) else (echo [FAIL] GET /api/admin/sync-logs)

echo.
echo =============================================
echo  Smoke test completed.
echo =============================================
endlocal
pause
