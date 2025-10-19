# Private Minecraft Server

[![CI Pipeline](https://github.com/dmccoystephenson/private-mc-server/workflows/CI%20Pipeline/badge.svg?branch=main)](https://github.com/dmccoystephenson/private-mc-server/actions)

A Docker-based private Minecraft server running the latest version of Minecraft (1.21.10) with Spigot for enhanced plugin support and performance.

## Features

- **Latest Minecraft Version**: Running Minecraft 1.21.10 with Spigot
- **Docker Containerized**: Easy deployment and management
- **Web Dashboard**: Built-in Spring Boot web application for server management
- **Configurable**: Environment-based configuration
- **Persistent Data**: Server data persists across container restarts
- **Easy Management**: Simple scripts for starting and stopping the server
- **RCON Support**: Send commands to the server remotely via web interface

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/)
- [Docker Compose](https://docs.docker.com/compose/install/)
- [Git](https://git-scm.com/downloads)

## Quick Start

1. **Clone the repository**
   ```bash
   git clone <your-repo-url>
   cd private-mc-server
   ```

2. **Configure the server**
   ```bash
   cp sample.env .env
   # Edit .env with your settings (see Configuration section)
   ```

3. **Build the web application**
   ```bash
   chmod +x build-webapp.sh
   ./build-webapp.sh
   ```

4. **Start the server**
   ```bash
   chmod +x up.sh down.sh
   ./up.sh
   ```
   
   **Note**: The first build will take 10-15 minutes as it downloads and compiles Spigot from source.

5. **Connect to your server**
   - Server address: `localhost:25565` (or your server's IP)
   - Web Dashboard: `https://localhost:8443` (or your server's IP with port 8443)
   - The server will take a few minutes to build on first run
   - **Note**: You'll see a security warning for the self-signed certificate. This is expected for development. See the Security section for production setup.

## Web Dashboard

The server includes a built-in web dashboard that provides:

- **Server Status**: Real-time view of server status, player count, and MOTD
- **Admin Console**: Send commands to the server using RCON
- **External Links**: Quick access to Dynmap, BlueMap, and other services
- **Secure Access**: HTTPS encryption with reverse proxy to protect credentials

Access the dashboard at `https://localhost:8443` (or your configured `WEB_HTTPS_PORT`). HTTP requests to port 8080 (or `WEB_HTTP_PORT`) will automatically redirect to HTTPS.

### SSL Certificates

The server uses self-signed SSL certificates by default for development. When you first access the web dashboard, your browser will show a security warning. This is expected and safe for local development.

**For production use**, replace the self-signed certificates with certificates from a trusted Certificate Authority:

1. Obtain SSL certificates (e.g., from [Let's Encrypt](https://letsencrypt.org/))
2. Place your certificate in `nginx/ssl/cert.pem`
3. Place your private key in `nginx/ssl/key.pem`
4. Restart the services with `./up.sh`

Alternatively, you can generate new self-signed certificates:
```bash
./scripts/generate-ssl-certs.sh
```

## Configuration

Copy `sample.env` to `.env` and modify the following settings:

### Essential Settings
- `OPERATOR_UUID`: Your Minecraft player UUID (get from [mcuuid.net](https://mcuuid.net/))
- `OPERATOR_NAME`: Your Minecraft username
- `SERVER_MOTD`: Message displayed in the server list
- `MAX_PLAYERS`: Maximum number of players allowed

**Note**: If `OPERATOR_UUID` and `OPERATOR_NAME` are not properly configured, the server will still start but you'll need to manually add operators using the `op <username>` command in the server console.

### Server Settings
- `DIFFICULTY`: Server difficulty (peaceful, easy, normal, hard)
- `GAMEMODE`: Default game mode (survival, creative, adventure, spectator)
- `PVP_ENABLED`: Enable/disable player vs player combat
- `ONLINE_MODE`: Enable Mojang authentication (set to false for offline/cracked servers)

### Docker Configuration (for Parallel Servers)

These settings allow you to run multiple server instances in parallel without conflicts:

- `CONTAINER_NAME`: Docker container name (default: `private-mc-server`)
- `HOST_PORT`: Host port for Minecraft server (default: `25565`)
- `HOST_RCON_PORT`: Host port for RCON (default: `25575`)
- `HOST_BLUEMAP_PORT`: Host port for BlueMap (default: `8100`)
- `VOLUME_NAME`: Docker volume name for persistent data (default: `mcserver`)

### Web Dashboard Configuration

- `WEB_CONTAINER_NAME`: Web application container name (default: `private-mc-webapp`)
- `NGINX_CONTAINER_NAME`: Nginx reverse proxy container name (default: `private-mc-nginx`)
- `WEB_HTTP_PORT`: HTTP port (redirects to HTTPS, default: `8080`)
- `WEB_HTTPS_PORT`: HTTPS port (default: `8443`)
- `RCON_PASSWORD`: Password for RCON authentication (default: `minecraft`)
- `ADMIN_USERNAME`: Username for admin console authentication (default: `admin`)
- `ADMIN_PASSWORD`: Password for admin console authentication (default: `admin`)
- `DYNMAP_URL`: URL to Dynmap web interface (optional)
- `BLUEMAP_URL`: URL to BlueMap web interface (optional)

**Note**: The RCON password must match between the server and web application for admin commands to work. Change the admin username and password from defaults in production for security. All connections to the web dashboard are encrypted using HTTPS to protect your credentials.

**Running Parallel Development Servers**: To run multiple servers simultaneously (e.g., for testing different configurations), create separate `.env` files with different values for these settings and use `docker compose --env-file <env-file>` to start each server.

Example for a second server:
```bash
# Create a separate env file for the second server
cp sample.env .env.dev2
# Edit .env.dev2 and change:
# - CONTAINER_NAME=private-mc-server-dev2
# - HOST_PORT=25566
# - HOST_RCON_PORT=25576
# - HOST_BLUEMAP_PORT=8101
# - VOLUME_NAME=mcserver-dev2
# - WEB_CONTAINER_NAME=private-mc-webapp-dev2
# - NGINX_CONTAINER_NAME=private-mc-nginx-dev2
# - WEB_HTTP_PORT=8081
# - WEB_HTTPS_PORT=8444

# Start the second server
docker compose --env-file .env.dev2 up -d --build
```

## Management

### Starting the Server
```bash
./up.sh
```
or
```bash
docker compose up -d --build
```

### Stopping the Server
```bash
./down.sh
```
or
```bash
docker compose down
```

**Note**: The server includes graceful shutdown handling that automatically sends the "stop" command to Minecraft when the container is stopped. This ensures that plugins save their data properly, preventing data loss that could occur with an abrupt termination.

### Viewing Server Logs
```bash
docker logs -f private-mc-server
```

**Note**: Replace `private-mc-server` with your `CONTAINER_NAME` value if you've customized it.

## File Management

### Backup Server Data
```bash
docker cp private-mc-server:/mcserver ./backup/
```

**Note**: Replace `private-mc-server` with your `CONTAINER_NAME` value if you've customized it.

### Restore Server Data
```bash
docker cp ./backup/ private-mc-server:/mcserver
docker compose restart
```

**Note**: Replace `private-mc-server` with your `CONTAINER_NAME` value if you've customized it.

### Deposit Box
The `deposit-box` directory is shared between your host system and the container at `/deposit-box`. Use it to transfer files to/from the server.

## Updating

### Automated Upgrade Script

The easiest way to upgrade your Minecraft server to a new version:

```bash
./upgrade.sh
```

This script automates the entire upgrade process:
- Stops the server gracefully
- Creates a timestamped backup automatically
- Prompts for the new version
- Updates configuration
- Rebuilds with the new version
- Starts the server

### Upgrade to a New Minecraft Version

For a comprehensive, step-by-step guide to upgrading your Minecraft server to a newer version with proper backup and rollback procedures, see the **[Upgrade Guide](UPGRADE-GUIDE.md)**.

The upgrade guide covers:
- Automated upgrade script usage (recommended)
- Manual step-by-step upgrade process
- Pre-upgrade backup procedures
- Rollback and restoration procedures
- Post-upgrade verification steps
- Troubleshooting common upgrade issues

### Quick Update (Without Version Change)

To update the container without changing the Minecraft version:

```bash
./down.sh
docker compose build --no-cache
./up.sh
```

## Troubleshooting

### Server Won't Start
- Check Docker logs: `docker logs private-mc-server` (use your `CONTAINER_NAME` value)
- Ensure all required environment variables are set
- Verify Docker and Docker Compose are installed

### Can't Connect to Server
- Ensure port 25565 is open/forwarded (or your custom `HOST_PORT` value)
- Check if `ONLINE_MODE` setting matches your client type
- Verify the server is running: `docker ps`

### Performance Issues
- Adjust memory allocation in `sample.env` by setting appropriate values
- Monitor system resources: `docker stats private-mc-server` (use your `CONTAINER_NAME` value)

## Security Notes

- **HTTPS Enabled**: All web dashboard connections are encrypted using HTTPS to protect admin credentials
- Change default operator settings in `.env`
- **Change default admin credentials**: Update `ADMIN_USERNAME` and `ADMIN_PASSWORD` in `.env`
- **Production SSL**: Replace self-signed certificates with trusted CA certificates (e.g., Let's Encrypt) for production
- Consider setting `ONLINE_MODE=true` for authentication
- Don't expose the server publicly without proper security measures
- Regularly backup your world data
- Keep `RCON_PASSWORD` secure and different from default values

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Development

### CI/CD Pipeline

This repository includes a comprehensive CI pipeline that automatically validates:

- **Shell Script Validation**: Syntax checking and ShellCheck linting for all bash scripts
- **Docker Configuration**: Validates Dockerfile and Docker Compose configurations
- **Environment Configuration**: Ensures all required environment variables are properly defined
- **Security Scanning**: Trivy security scanning for vulnerabilities
- **Integration Testing**: End-to-end validation of the complete setup

### Running Local CI Checks

Before submitting changes, you can run the same validation checks locally:

```bash
./scripts/ci-local.sh
```

This will run basic validation checks that mirror the CI pipeline to catch issues early.

### CI Pipeline Status

The CI pipeline runs on:
- Every push to `main` and `develop` branches
- Every pull request to `main`

Check the [Actions tab](https://github.com/dmccoystephenson/private-mc-server/actions) for detailed CI results and logs.

## Contributing

Feel free to submit issues and enhancement requests!
