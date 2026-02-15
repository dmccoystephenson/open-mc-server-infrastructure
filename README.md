# Open Minecraft Server Infrastructure

[![CI Pipeline](https://github.com/dmccoystephenson/private-mc-server/workflows/CI%20Pipeline/badge.svg?branch=main)](https://github.com/dmccoystephenson/private-mc-server/actions)

An open, community-agnostic, Docker-based Minecraft server infrastructure running the latest version of Minecraft (1.21.10) with Spigot for enhanced plugin support and performance. Highly configurable and customizable for any use case.

## Features

- **Latest Minecraft Version**: Running Minecraft 1.21.10 with Spigot
- **Docker Containerized**: Easy deployment and management
- **Web Dashboard**: Built-in Spring Boot web application for server management
- **Automated Backups**: Scheduled backups with automatic cleanup and size management
- **Alert Notifications**: Discord notifications for server events and admin alerts
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
   cd open-mc-server-infrastructure
   ```

2. **Configure the server**
   ```bash
   cp sample.env .env
   # Edit .env with your settings (see Configuration section)
   ```

3. **Start the server**
   ```bash
   chmod +x up.sh down.sh
   ./up.sh
   ```
   
   **Note**: The first build will take 10-15 minutes as it downloads and compiles Spigot from source. The JARs for all services will be built automatically during the Docker build process.

4. **Connect to your server**
   - Server address: `localhost:25565` (or your server's IP)
   - Web Dashboard: `https://localhost:8443` (or your server's IP with port 8443)
   - The server will take a few minutes to build on first run
   - **Note**: You'll see a security warning for the self-signed certificate. This is expected for development. See the Security section for production setup.

## Web Dashboard

The server includes a built-in web dashboard that provides:

- **Server Status**: Real-time view of server status, player count, and MOTD
- **Admin Console**: Send commands to the server using RCON
- **External Links**: Quick access to Dynmap, BlueMap, Accord Chat, and other services
- **Activity Tracker Integration**: View player statistics and leaderboards (optional)
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

### Activity Tracker Integration

The web dashboard can optionally integrate with the [Activity Tracker plugin](https://github.com/Dans-Plugins/Activity-Tracker) to display player statistics and leaderboards. When enabled, the dashboard will show:

- **Server Statistics**: Number of unique players and total logins
- **Player Leaderboard**: Top 10 players ranked by hours played, with total logins

To enable Activity Tracker integration:

1. Install the Activity Tracker plugin on your Minecraft server
2. Configure the plugin to enable its REST API (see plugin documentation)
3. Set the following environment variables in your `.env` file:
   ```bash
   ACTIVITY_TRACKER_ENABLED=true
   ACTIVITY_TRACKER_URL=http://localhost:8080
   ```
4. Restart the web application with `./up.sh`

The Activity Tracker data will automatically refresh with the server status updates. If the Activity Tracker API is not available, the sections will be hidden without affecting other dashboard functionality.

### Accord Chat Integration

The infrastructure includes built-in support for [Accord Chat](https://github.com/dmccoystephenson/accord-prototype), a real-time web-based chat application. When enabled, players and administrators can communicate through a modern web interface accessible from the dashboard.

To enable Accord Chat integration:

1. Set the following environment variables in your `.env` file:
   ```bash
   ACCORD_CHAT_URL=http://localhost:3000
   ACCORD_CHAT_PORT=3000
   ACCORD_BACKEND_PORT=8082
   DOCKER_HOST_IP=localhost
   ```
   
2. For accessing from other machines on your network, set `DOCKER_HOST_IP` to your server's IP address:
   ```bash
   DOCKER_HOST_IP=192.168.1.100
   ```

3. Start or restart the services with `./up.sh`

The Accord Chat service will be available at `http://localhost:3000` (or your configured port), and a "Chat" link will appear in the web dashboard's External Services section.

**Note**: By default, Accord Chat uses an in-memory H2 database, so chat messages are not persisted between restarts. For production use with persistent storage, you'll need to configure a persistent database (see the [Accord Chat documentation](https://github.com/dmccoystephenson/accord-prototype)). Additionally, CORS is set to allow all origins (`*`) by default for development. For production, set `ACCORD_CORS_ALLOWED_ORIGINS` to your specific domain(s).

#### Updating Accord Chat

Since Accord Chat is under active development, you can easily pull updates and restart the service using the update script:

```bash
./update-accord.sh
```

Or to pull from a specific branch:

```bash
./update-accord.sh --branch feature-branch-name
```

For automated/non-interactive environments (CI/CD, scripts):

```bash
./update-accord.sh --yes
```

The update script will:
1. Pull the latest code from the specified Git branch (default: `main`)
2. Check for uncommitted changes and warn if present
3. Ensure `accord-chat/.env` exists (created from `sample.env` if needed)
4. Restart only the Accord backend and webapp services without affecting the rest of the infrastructure

**Note on Configuration**: Accord services are defined in `accord-chat/compose.yml` and referenced via `compose.accord.yml` in the root directory. The root `compose.yml` includes `compose.accord.yml`, which defines the accord-backend and accord-webapp services based on the accord-chat definitions. This approach eliminates duplication while allowing infrastructure-specific overrides (ports, container names). To customize Accord configuration, edit `accord-chat/.env` directly.

This streamlined process allows you to quickly iterate on Accord Chat development without disrupting your Minecraft server or other services.

**Example workflow:**
```bash
# Update Accord from main branch
./update-accord.sh

# Or update from a specific development branch
./update-accord.sh --branch experimental-features

# Non-interactive mode for automation
./update-accord.sh --yes --branch main

# Customize Accord configuration
vim accord-chat/.env

# View logs to verify the update
docker compose logs -f accord-backend accord-webapp
```

For more details, run `./update-accord.sh --help`.

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

- `CONTAINER_NAME`: Docker container name (default: `open-mc-server`)
- `HOST_PORT`: Host port for Minecraft server (default: `25565`)
- `HOST_RCON_PORT`: Host port for RCON (default: `25575`)
- `HOST_BLUEMAP_PORT`: Host port for BlueMap (default: `8100`)
- `VOLUME_NAME`: Docker volume name for persistent data (default: `mcserver`)

### Web Dashboard Configuration

- `WEB_CONTAINER_NAME`: Web application container name (default: `open-mc-webapp`)
- `NGINX_CONTAINER_NAME`: Nginx reverse proxy container name (default: `open-mc-nginx`)
- `WEB_HTTP_PORT`: HTTP port (redirects to HTTPS, default: `8080`)
- `WEB_HTTPS_PORT`: HTTPS port (default: `8443`)
- `RCON_PASSWORD`: Password for RCON authentication (default: `minecraft`)
- `ADMIN_USERNAME`: Username for admin console authentication (default: `admin`)
- `ADMIN_PASSWORD`: Password for admin console authentication (default: `admin`)
- `DYNMAP_URL`: URL to Dynmap web interface (optional)
- `BLUEMAP_URL`: URL to BlueMap web interface (optional)
- `ACCORD_CHAT_URL`: URL to Accord Chat web interface (optional, e.g., `http://localhost:3000`)
- `ACCORD_CHAT_PORT`: Host port for Accord Chat webapp (default: `3000`)
- `ACCORD_BACKEND_PORT`: Host port for Accord Chat backend API (default: `8082`)
- `DOCKER_HOST_IP`: Docker host IP for accessing services from external browsers (default: `localhost`)
- `ACTIVITY_TRACKER_URL`: URL to Activity Tracker plugin REST API (optional, e.g., `http://localhost:8080`)
- `ACTIVITY_TRACKER_ENABLED`: Enable Activity Tracker integration (default: `false`)

**Note**: The RCON password must match between the server and web application for admin commands to work. Change the admin username and password from defaults in production for security. All connections to the web dashboard are encrypted using HTTPS to protect your credentials.

### Backup Manager Configuration

- `BACKUP_CONTAINER_NAME`: Backup manager container name (default: `open-mc-backup-manager`)
- `BACKUP_MAX_SIZE_MB`: Maximum size of backups directory in MB (default: `10240` = 10GB)
- `BACKUP_SCHEDULE`: Cron expression for backup schedule (default: `0 0 2 * * ?` = 2 AM daily)

See [backup-manager/README.md](backup-manager/README.md) for detailed cron expression examples and configuration.

### Alert Manager Configuration

- `ALERT_CONTAINER_NAME`: Alert manager container name (default: `open-mc-alert-manager`)
- `ALERT_PORT`: Alert manager API port (default: `8090`)
- `DISCORD_WEBHOOK_URL`: Discord webhook URL for sending notifications (optional)
- `DISCORD_ENABLED`: Enable/disable Discord notifications (default: `false`)

**Alert Toggles** - Fine-grained control over which events trigger alerts:
- `ALERTS_SERVER_START`: Alert when server starts (default: `true`)
- `ALERTS_SERVER_STOP`: Alert when server stops gracefully (default: `true`)
- `ALERTS_SERVER_CRASH`: Alert when server crashes unexpectedly (default: `true`)
- `ALERTS_BACKUP_SUCCESS`: Alert when backup completes successfully (default: `true`)
- `ALERTS_BACKUP_FAILURE`: Alert when backup fails (default: `true`)
- `ALERTS_UPGRADE_START`: Alert when upgrade process begins (default: `true`)
- `ALERTS_UPGRADE_COMPLETE`: Alert when upgrade completes successfully (default: `true`)
- `ALERTS_UPGRADE_FAILURE`: Alert when upgrade fails (default: `true`)
- `ALERTS_CONFIG_WARNING`: Alert when server starts with configuration warnings (default: `false`)

To enable Discord notifications:
1. Create a webhook in your Discord server (Server Settings → Integrations → Webhooks)
2. Copy the webhook URL and add it to your `.env` file
3. Set `DISCORD_ENABLED=true`

The alert manager API is accessible on the configured port (default: 8090) for testing and integration from the host machine.

See [alert-manager/README.md](alert-manager/README.md) for detailed configuration and usage examples.

**Running Parallel Development Servers**: To run multiple servers simultaneously (e.g., for testing different configurations), create separate `.env` files with different values for these settings and use `docker compose --env-file <env-file>` to start each server.

Example for a second server: Create a separate `.env` file with different values for `CONTAINER_NAME`, `HOST_PORT`, `HOST_RCON_PORT`, `HOST_BLUEMAP_PORT`, `VOLUME_NAME`, `WEB_CONTAINER_NAME`, `NGINX_CONTAINER_NAME`, `BACKUP_CONTAINER_NAME`, `ALERT_CONTAINER_NAME`, `ALERT_PORT`, `WEB_HTTP_PORT`, and `WEB_HTTPS_PORT`, then start with `docker compose --env-file .env.dev2 up -d --build`.

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

**Note**: The server includes graceful shutdown handling that automatically warns players before stopping. When a shutdown is initiated, players will receive countdown warnings at 30, 20, 10, and 5 seconds before the server stops. The server then sends the "stop" command to Minecraft, ensuring that plugins save their data properly and preventing data loss that could occur with an abrupt termination. The Docker Compose configuration includes a 45-second grace period to allow sufficient time for the warning sequence and graceful shutdown to complete.

### Viewing Server Logs
```bash
docker logs -f open-mc-server
```

**Note**: Replace `open-mc-server` with your `CONTAINER_NAME` value if you've customized it.

## File Management

### Backup Server Data

#### Automated Scheduled Backups (Recommended)

The infrastructure includes a **backup-manager** service that automatically backs up the server data:

- **Automatic Scheduling**: Runs daily at 2 AM (configurable via `BACKUP_SCHEDULE` in `.env`)
- **Size Management**: Automatically removes old backups when the backup directory exceeds the configured size limit (default: 10GB)
- **Containerized**: Runs in its own container for isolation and reliability

To configure automated backups, set the following in your `.env` file:

```bash
# Maximum size of backups directory in MB (default: 10GB)
BACKUP_MAX_SIZE_MB=10240

# Backup schedule (cron expression, default: 2 AM daily)
BACKUP_SCHEDULE=0 0 2 * * ?
```

View backup-manager logs:
```bash
docker logs -f open-mc-backup-manager
```

See [backup-manager/README.md](backup-manager/README.md) for detailed configuration options.

#### Manual Backup

The backup-manager service automatically creates backups on a schedule (default: 2 AM daily). Backups are stored in the `./backups/` directory with timestamped names like `backup-20241211-020000`.

To trigger a manual backup immediately:

```bash
./trigger-backup.sh
```

This script uses the backup-manager REST API to create an immediate backup. Alternatively, you can restart the backup-manager container:

```bash
docker restart open-mc-backup-manager
```

Or use Docker commands to manually copy server data:

```bash
docker cp open-mc-server:/mcserver ./backup/
```

**Note**: Replace `open-mc-server` with your `CONTAINER_NAME` value if you've customized it.

### Restore Server Data
```bash
docker cp ./backup/ open-mc-server:/mcserver
docker compose restart
```

**Note**: Replace `open-mc-server` with your `CONTAINER_NAME` value if you've customized it.

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
- Check Docker logs: `docker logs open-mc-server` (use your `CONTAINER_NAME` value)
- Ensure all required environment variables are set
- Verify Docker and Docker Compose are installed

### Can't Connect to Server
- Ensure port 25565 is open/forwarded (or your custom `HOST_PORT` value)
- Check if `ONLINE_MODE` setting matches your client type
- Verify the server is running: `docker ps`

### Performance Issues
- Adjust memory allocation in `sample.env` by setting appropriate values
- Monitor system resources: `docker stats open-mc-server` (use your `CONTAINER_NAME` value)

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

### Modules

The infrastructure consists of several Spring Boot modules:

#### Alert Manager
Handles alert notifications to Discord and sends messages to Minecraft players via RCON.
- **Port**: 8090
- **Location**: `alert-manager/`
- **Documentation**: [alert-manager/README.md](alert-manager/README.md)

#### Backup Manager  
Automated backup service with scheduling and size management.
- **Port**: 8091
- **Location**: `backup-manager/`
- **Documentation**: [backup-manager/README.md](backup-manager/README.md)

#### Minecraft Wrapper
Spring Boot service providing testable, REST-accessible wrapper functionality for Minecraft server management. This module is integrated into the main Minecraft server container.
- **Location**: `minecraft-wrapper/`
- **Documentation**: [minecraft-wrapper/README.md](minecraft-wrapper/README.md)
- **Features**:
  - Unit-tested server lifecycle management (15 tests)
  - REST API for server status, commands, and messaging (port 8092)
  - Graceful shutdown with player warnings
  - Alert integration
  - Process management for Minecraft server

#### Web App
Spring Boot web dashboard for server management and monitoring.
- **Port**: 8080 (behind nginx proxy on 8443)
- **Location**: `web-app/`

### CI/CD Pipeline

This repository includes a comprehensive CI pipeline that automatically validates:

- **Shell Script Validation**: Syntax checking and ShellCheck linting for all bash scripts
- **Docker Configuration**: Validates Dockerfile and Docker Compose configurations
- **Environment Configuration**: Ensures all required environment variables are properly defined
- **Security Scanning**: Trivy security scanning for vulnerabilities
- **Server Run Testing**: Actually runs the Minecraft server to verify it starts, operates, and stops correctly
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
