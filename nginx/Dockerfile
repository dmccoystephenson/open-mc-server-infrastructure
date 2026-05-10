FROM nginx:latest

# Install openssl and create non-root user
RUN apt-get update && \
    apt-get install -y openssl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -r -g 1000 appgroup && \
    useradd -r -u 1000 -g appgroup appuser

# Copy nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Create SSL directory, writable by appuser
RUN mkdir -p /etc/nginx/ssl && \
    chown appuser:appgroup /etc/nginx/ssl

# Copy entrypoint script
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 80 443

USER appuser

ENTRYPOINT ["/entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
