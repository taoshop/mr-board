#!/bin/bash
# ============================================================
# Generate Self-Signed SSL Certificate for MR Board (Linux/Mac)
# Requires OpenSSL
#
# For production: Replace with Let's Encrypt / purchased cert.
#   certbot certonly --standalone -d your-domain.com
#   Copy fullchain.pem → nginx/ssl/mr-board.crt
#   Copy privkey.pem   → nginx/ssl/mr-board.key
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SSL_DIR="$SCRIPT_DIR/ssl"

mkdir -p "$SSL_DIR"

echo "Generating RSA private key (2048-bit)..."

# MSYS2 (Git Bash on Windows) converts strings starting with "/" to paths.
# Use "//" prefix to prevent this: //C=CN\ST=...
SUBJ="//C=CN\ST=Shanghai\L=Shanghai\O=MRBoard\OU=Dev\CN=localhost"

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "$SSL_DIR/mr-board.key" \
    -out "$SSL_DIR/mr-board.crt" \
    -subj "$SUBJ" \
    -addext "subjectAltName=DNS:localhost,DNS:mr-board.local,IP:127.0.0.1"

echo ""
echo "[OK] Certificate generated successfully!"
echo "  Certificate: $SSL_DIR/mr-board.crt"
echo "  Private Key: $SSL_DIR/mr-board.key"
echo ""
echo "To trust this cert (macOS):"
echo "  sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain $SSL_DIR/mr-board.crt"
echo ""
echo "To trust this cert (Linux):"
echo "  sudo cp $SSL_DIR/mr-board.crt /usr/local/share/ca-certificates/mr-board.crt"
echo "  sudo update-ca-certificates"
