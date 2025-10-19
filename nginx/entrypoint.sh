#!/bin/sh

# Entrypoint script for nginx container
# Generates self-signed SSL certificates if they don't exist

set -e

SSL_CERT="/etc/nginx/ssl/cert.pem"
SSL_KEY="/etc/nginx/ssl/key.pem"

# Check if SSL certificates exist
if [ ! -f "$SSL_CERT" ] || [ ! -f "$SSL_KEY" ]; then
    echo "SSL certificates not found. Generating self-signed certificates..."
    
    # Install openssl if not present
    if ! command -v openssl >/dev/null 2>&1; then
        echo "Installing openssl..."
        apk add --no-cache openssl
    fi
    
    # Generate self-signed certificate
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout "$SSL_KEY" \
        -out "$SSL_CERT" \
        -subj "/C=US/ST=State/L=City/O=Organization/CN=localhost" \
        2>/dev/null
    
    echo "Self-signed SSL certificates generated successfully."
    echo "For production, replace with certificates from a trusted CA."
else
    echo "Using existing SSL certificates."
fi

# Execute the command passed to the container
exec "$@"
