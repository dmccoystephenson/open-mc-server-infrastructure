FROM nginx:latest

# Install openssl
RUN apt-get update && \
    apt-get install -y openssl && \
    rm -rf /var/lib/apt/lists/*

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
