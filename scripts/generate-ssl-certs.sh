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
echo "   To use custom certificates:"
echo "   1. Place your certificate in ${SSL_DIR}/cert.pem"
echo "   2. Place your private key in ${SSL_DIR}/key.pem"
echo "   3. Restart the services with ./up.sh"
