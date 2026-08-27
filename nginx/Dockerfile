FROM nginx:latest

# Install openssl; libcap2-bin provides setcap so the nginx binary can bind
# to ports 80/443 as a non-root user without runtime CAP_NET_BIND_SERVICE
# (Kubernetes adds the capability via securityContext; setcap covers Docker Compose).
# curl is used by the Docker Compose healthcheck (Kubernetes uses a kubelet httpGet probe instead).
RUN apt-get update && \
    apt-get install -y openssl libcap2-bin curl && \
    rm -rf /var/lib/apt/lists/* && \
    setcap cap_net_bind_service=+ep /usr/sbin/nginx

# Copy nginx configuration.  It is owned by UID 1000 so that entrypoint.sh can
# substitute NGINX_MAX_BODY_SIZE into it at startup; on Kubernetes this file is
# replaced by a read-only ConfigMap mount and the substitution is skipped.
COPY --chown=1000:1000 nginx.conf /etc/nginx/nginx.conf

# Create SSL directory owned by UID 1000 so entrypoint can write certs without root
RUN mkdir -p /etc/nginx/ssl && chown 1000:1000 /etc/nginx/ssl

# Directory for the optional route fragments entrypoint.sh generates (BlueMap).
# Owned by UID 1000 for the same reason as nginx.conf above: /etc/nginx itself is
# root-owned, so the container user needs its own writable directory here.
RUN mkdir -p /etc/nginx/omcsi.d && chown 1000:1000 /etc/nginx/omcsi.d

# Copy entrypoint script
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 80 443

USER 1000

ENTRYPOINT ["/entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
