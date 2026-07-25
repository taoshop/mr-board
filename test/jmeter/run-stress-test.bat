@echo off
REM ============================================================
REM MR Board - JMeter Concurrent Stress Test (Windows)
REM Prerequisites: JMeter installed and in PATH
REM ============================================================

setlocal

set SCRIPT_DIR=%~dp0
set RESULT_DIR=%SCRIPT_DIR%results

if not exist "%RESULT_DIR%" mkdir "%RESULT_DIR%"

set BASE_URL=%1
if "%BASE_URL%"=="" set BASE_URL=http://localhost:8080

set ADMIN_USER=%2
if "%ADMIN_USER%"=="" set ADMIN_USER=admin

set ADMIN_PASS=%3
if "%ADMIN_PASS%"=="" set ADMIN_PASS=Admin@123

set CONCURRENT_USERS=%4
if "%CONCURRENT_USERS%"=="" set CONCURRENT_USERS=100

set RAMP_UP_SECONDS=%5
if "%RAMP_UP_SECONDS%"=="" set RAMP_UP_SECONDS=30

set DURATION_SECONDS=%6
if "%DURATION_SECONDS%"=="" set DURATION_SECONDS=120

echo =============================================
echo  MR Board - Concurrent Stress Test
echo =============================================
echo   Base URL:           %BASE_URL%
echo   Admin User:         %ADMIN_USER%
echo   Concurrent Users:   %CONCURRENT_USERS%
echo   Ramp-up Seconds:    %RAMP_UP_SECONDS%
echo   Duration Seconds:   %DURATION_SECONDS%
echo   Result Directory:   %RESULT_DIR%
echo =============================================
echo.

set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%-%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%

jmeter -n -t "%SCRIPT_DIR%mr-board-stress-test.jmx" ^
  -JbaseUrl="%BASE_URL%" ^
  -JadminUser="%ADMIN_USER%" ^
  -JadminPass="%ADMIN_PASS%" ^
  -JconcurrentUsers="%CONCURRENT_USERS%" ^
  -JrampUpSeconds="%RAMP_UP_SECONDS%" ^
  -JdurationSeconds="%DURATION_SECONDS%" ^
  -JresultDir="%RESULT_DIR%" ^
  -l "%RESULT_DIR%\stress-test-%TIMESTAMP%.jtl" ^
  -e -o "%RESULT_DIR%\html-report"

echo.
echo [OK] Stress test completed!
echo   JTL results: %RESULT_DIR%
echo   HTML report: %RESULT_DIR%\html-report\index.html

endlocal
pause
