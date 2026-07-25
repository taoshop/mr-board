@echo off
REM ============================================================
REM Generate Self-Signed SSL Certificate for MR Board (Windows)
REM Requires OpenSSL installed (e.g. Git Bash, or standalone)
REM
REM For production: Replace with Let's Encrypt / purchased cert.
REM   certbot certonly --standalone -d your-domain.com
REM   Copy fullchain.pem → nginx/ssl/mr-board.crt
REM   Copy privkey.pem   → nginx/ssl/mr-board.key
REM ============================================================

set SCRIPT_DIR=%~dp0
set SSL_DIR=%SCRIPT_DIR%ssl

if not exist "%SSL_DIR%" mkdir "%SSL_DIR%"

echo Generating RSA private key (2048-bit)...
openssl req -x509 -nodes -days 365 -newkey rsa:2048 ^
    -keyout "%SSL_DIR%\mr-board.key" ^
    -out "%SSL_DIR%\mr-board.crt" ^
    -subj "/C=CN/ST=Shanghai/L=Shanghai/O=MRBoard/OU=Dev/CN=localhost" ^
    -addext "subjectAltName=DNS:localhost,DNS:mr-board.local,IP:127.0.0.1"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [OK] Certificate generated successfully!
    echo   Certificate: %SSL_DIR%mr-board.crt
    echo   Private Key: %SSL_DIR%mr-board.key
    echo.
    echo To trust this cert in Windows (dev only):
    echo   certutil -addstore -user "Root" "%SSL_DIR%mr-board.crt"
) else (
    echo.
    echo [ERROR] Certificate generation failed. Is OpenSSL installed?
    echo Install: winget install ShiningLight.OpenSSL
)
pause
