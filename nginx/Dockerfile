FROM nginx:latest

# Install openssl; libcap2-bin provides setcap so the nginx binary can bind
# to ports 80/443 as a non-root user without runtime CAP_NET_BIND_SERVICE
# (Kubernetes adds the capability via securityContext; setcap covers Docker Compose).
RUN apt-get update && \
    apt-get install -y openssl libcap2-bin && \
    rm -rf /var/lib/apt/lists/* && \
    setcap cap_net_bind_service=+ep /usr/sbin/nginx

# Copy nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Create SSL directory owned by UID 1000 so entrypoint can write certs without root
RUN mkdir -p /etc/nginx/ssl && chown 1000:1000 /etc/nginx/ssl

# Copy entrypoint script
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 80 443

USER 1000

ENTRYPOINT ["/entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
