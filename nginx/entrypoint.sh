#!/bin/bash

# Entrypoint script for nginx container
# Generates self-signed SSL certificates if they don't exist, applies the
# configurable upload size limit and timeouts, and writes the optional BlueMap
# route fragment

set -e

SSL_CERT="/etc/nginx/ssl/cert.pem"
SSL_KEY="/etc/nginx/ssl/key.pem"
NGINX_CONF="/etc/nginx/nginx.conf"
FRAGMENT_DIR="/etc/nginx/omcsi.d"
BLUEMAP_FRAGMENT="$FRAGMENT_DIR/bluemap.conf"

# Check if SSL certificates exist
if [ ! -f "$SSL_CERT" ] || [ ! -f "$SSL_KEY" ]; then
    echo "SSL certificates not found. Generating self-signed certificates..."
    
    # Generate self-signed certificate
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout "$SSL_KEY" \
        -out "$SSL_CERT" \
        -subj "/C=US/ST=State/L=City/O=Organization/CN=localhost"
    
    echo "Self-signed SSL certificates generated successfully."
    echo "For production, replace with certificates from a trusted CA."
else
    echo "Using existing SSL certificates."
fi

# Apply the configurable upload size limit.
#
# Under Docker Compose nginx.conf is baked into the image and writable by the
# container user, so NGINX_MAX_BODY_SIZE is substituted in here. On Kubernetes
# nginx.conf is a read-only ConfigMap subPath mount that the Helm chart has
# already rendered from .Values.nginx.maxBodySize, so the substitution is
# skipped rather than failing on the read-only filesystem.
if [ -w "$NGINX_CONF" ]; then
    NGINX_MAX_BODY_SIZE="${NGINX_MAX_BODY_SIZE:-100M}"
    # Rewritten by truncating the file rather than with `sed -i`: /etc/nginx is
    # root-owned, so the container user (UID 1000) cannot create sed's temporary
    # file alongside it, but it can rewrite the file itself.
    sed "s/client_max_body_size [^;]*;/client_max_body_size ${NGINX_MAX_BODY_SIZE};/" \
        "$NGINX_CONF" > /tmp/nginx.conf.tmp
    cat /tmp/nginx.conf.tmp > "$NGINX_CONF"
    rm -f /tmp/nginx.conf.tmp
    echo "Maximum request body size set to ${NGINX_MAX_BODY_SIZE}."

    # Upload-route timeouts. Only the two lines tagged "# upload" are rewritten,
    # so the dashboard's own 60s proxy timeouts are left alone.
    NGINX_UPLOAD_TIMEOUT="${NGINX_UPLOAD_TIMEOUT:-3600s}"
    sed -E "s/(proxy_(send|read)_timeout) [^;]*; # upload/\1 ${NGINX_UPLOAD_TIMEOUT}; # upload/" \
        "$NGINX_CONF" > /tmp/nginx.conf.tmp
    cat /tmp/nginx.conf.tmp > "$NGINX_CONF"
    rm -f /tmp/nginx.conf.tmp
    echo "Upload route timeout set to ${NGINX_UPLOAD_TIMEOUT}."
else
    echo "$NGINX_CONF is not writable; using its configured upload size limit and timeouts."
fi

# Optional BlueMap route.
#
# Mirrors nginx.bluemap.{enabled,path} in the Helm chart, which renders the same
# location block straight into its ConfigMap. Only generated when the running
# nginx.conf actually includes the fragment directory — the chart's ConfigMap
# does not, so on Kubernetes this is skipped and the chart stays authoritative.
if grep -q "$FRAGMENT_DIR" "$NGINX_CONF"; then
    # Regenerated from scratch every start: the fragment lives in the container's
    # writable layer, so a stale one would otherwise survive a `restart: always`
    # restart after NGINX_BLUEMAP_ENABLED was turned back off.
    rm -f "$BLUEMAP_FRAGMENT"

    if [ "${NGINX_BLUEMAP_ENABLED:-false}" = "true" ]; then
        NGINX_BLUEMAP_PATH="${NGINX_BLUEMAP_PATH:-/map/}"
        # Quoted heredoc so nginx's own $host / $scheme variables survive the
        # shell; the path is the only thing substituted.
        sed "s|__BLUEMAP_PATH__|${NGINX_BLUEMAP_PATH}|" > "$BLUEMAP_FRAGMENT" <<'FRAGMENT'
# BlueMap's webapp, served by the plugin inside the wrapper container.
# proxy_pass carries a trailing slash so the configured path maps onto BlueMap's
# own root; without it every asset path would be prefixed twice and 404.
location __BLUEMAP_PATH__ {
    proxy_pass http://minecraft-wrapper:8100/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    # Tile requests are many and small; keep them cheap.
    proxy_http_version 1.1;
    proxy_set_header Connection "";

    proxy_connect_timeout 60s;
    proxy_send_timeout 60s;
    proxy_read_timeout 60s;
}
FRAGMENT
        echo "BlueMap route enabled at ${NGINX_BLUEMAP_PATH}."
    else
        echo "BlueMap route disabled."
    fi
fi

# Execute the command passed to the container
exec "$@"
