#!/bin/bash

# Script to generate self-signed SSL certificates for the web application
# For production use, replace these with certificates from a trusted CA

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SSL_DIR="${SCRIPT_DIR}/../nginx/ssl"

echo "🔐 Generating SSL certificates..."

# Create SSL directory if it doesn't exist
mkdir -p "${SSL_DIR}"

# Generate self-signed certificate
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "${SSL_DIR}/key.pem" \
    -out "${SSL_DIR}/cert.pem" \
    -subj "/C=US/ST=State/L=City/O=Organization/CN=localhost"

echo "✓ SSL certificates generated successfully in ${SSL_DIR}"
echo ""
echo "⚠️  IMPORTANT: These are self-signed certificates for development."
echo "   For production, use certificates from a trusted Certificate Authority (CA)"
echo "   such as Let's Encrypt."
echo ""
echo "   These files are not mounted into the nginx container automatically —"
echo "   its certs live in the 'nginx-ssl' named Docker volume. To use these"
echo "   (or your own custom cert/key), copy them in with docker cp:"
echo "   1. Start the stack so the nginx-ssl volume exists: ./up.sh"
echo "   2. docker cp ${SSL_DIR}/cert.pem open-mc-nginx:/etc/nginx/ssl/cert.pem"
echo "   3. docker cp ${SSL_DIR}/key.pem open-mc-nginx:/etc/nginx/ssl/key.pem"
echo "   4. docker compose restart nginx"
