# Open Minecraft Server Infrastructure

[![CI Pipeline](https://github.com/dmccoystephenson/private-mc-server/workflows/CI%20Pipeline/badge.svg?branch=main)](https://github.com/dmccoystephenson/private-mc-server/actions)

An open, community-agnostic, Docker-based Minecraft server infrastructure running the latest version of Minecraft (1.21.10) with Spigot for enhanced plugin support and performance. Highly configurable and customizable for any use case.

## Features

- **Multiple Server Types**: Support for both Spigot (plugins) and Forge (mods) servers
  - **Spigot**: Running Minecraft 1.21.10 with plugin support
  - **Forge**: Running Minecraft 1.21.1 with All the Mods 10 (ATM10) pre-installed
- **Docker Containerized**: Easy deployment and management
- **Web Dashboard**: Built-in Spring Boot web application for server management
- **Automated Backups**: Scheduled backups with automatic cleanup and size management
- **Alert Notifications**: Discord notifications for server events and admin alerts
- **Configurable**: Environment-based configuration with easy server type switching
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
   # Set SERVER_TYPE=spigot for plugins or SERVER_TYPE=forge for mods
   ```

3. **Start the server**
   ```bash
   chmod +x up.sh down.sh
   ./up.sh
   ```
   
   **Note**: The first build will take 10-15 minutes for Spigot (compiles from source) or 15-20 minutes for Forge with ATM10 (downloads mods). The JARs for all services will be built automatically during the Docker build process.

4. **Connect to your server**
   - Server address: `localhost:25565` (or your server's IP)
   - Web Dashboard: `https://localhost:8443` (or your server's IP with port 8443)
   - The server will take a few minutes to build on first run
   - **Note**: You'll see a security warning for the self-signed certificate. This is expected for development. See the Security section for production setup.
   - **For Forge/ATM10 servers**: See the "Forge Server with ATM10" section below for important connection information.

## Web Dashboard

The server includes a built-in web dashboard that provides:

- **Server Status**: Real-time view of server status, player count, and MOTD
- **Admin Console**: Send commands to the server using RCON
- **External Links**: Quick access to Dynmap, BlueMap, and other services
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

## Forge Server with ATM10

The infrastructure supports running a Forge server with **All the Mods 10 (ATM10)** pre-installed. This allows you to run a modded Minecraft server with over 400+ mods.

### Choosing Forge Server

To use a Forge server instead of Spigot:

1. Set `SERVER_TYPE=forge` in your `.env` file
2. The server will use Minecraft 1.21.1 (required by ATM10)
3. All the Mods 10 will be automatically installed with all its mods

### Connecting to Forge/ATM10 Server

**IMPORTANT**: To connect to a Forge server with ATM10, you must:

1. **Install the ATM10 Client Modpack**: 
   - Download and install the ATM10 client from [CurseForge](https://www.curseforge.com/minecraft/modpacks/all-the-mods-10) or through a launcher like CurseForge, Prism Launcher, or MultiMC
   - The client and server mod versions must match for compatibility

2. **Use the Correct Minecraft Version**:
   - ATM10 requires Minecraft 1.21.1 with Forge
   - Vanilla clients or other Minecraft versions will not be able to connect

3. **Connection Address**:
   - Server address: `localhost:25565` (or your server's IP)
   - The connection process is the same as Spigot, but requires the modded client

### Managing Mods

- **Mods Location**: All mods are stored in `/mcserver/mods` inside the container
- **Adding Mods**: Place additional compatible Forge 1.21.1 mods in the `deposit-box/mods` directory and copy them to the server's mods folder
- **Removing Mods**: Remove mod files from the mods directory and restart the server
- **Mod Compatibility**: Ensure all added mods are compatible with Forge 1.21.1 and ATM10

### Forge Server Performance

Modded servers require more resources than vanilla or Spigot servers:

- **Recommended RAM**: 6-8GB minimum (set via `JAVA_OPTS=-Xmx6G -Xms4G` in `.env`)
- **Startup Time**: Initial startup may take 5-10 minutes as mods initialize
- **Storage**: ATM10 with mods requires approximately 2-3GB of disk space

### Switching Between Server Types

You can switch between Spigot and Forge servers:

1. Stop the server: `./down.sh`
2. Change `SERVER_TYPE` in your `.env` file
3. **IMPORTANT**: Set `OVERWRITE_EXISTING_SERVER=true` in your `.env` file to start fresh (this will delete your existing world)
4. Rebuild and start: `./up.sh`

**Warning**: Server types are not compatible. Worlds and data from a Spigot server cannot be directly used on a Forge server and vice versa.

**What happens if you don't set `OVERWRITE_EXISTING_SERVER=true`?**

If you switch server types without setting `OVERWRITE_EXISTING_SERVER=true`, the server will attempt to start using the existing world and data from the previous server type. This will likely result in:
- Startup errors or crashes
- Corrupted world data
- Server failing to start completely
- Data format incompatibility errors in the logs

It is **strongly recommended** to set `OVERWRITE_EXISTING_SERVER=true` when switching server types to avoid these issues. Otherwise, you must manually backup and delete the old world data before starting the new server type.

### Updating ATM10 Version

When a new version of All the Mods 10 is released, you can update your Forge server:

1. Find the new ATM10 version information:
   - Visit the [ATM10 CurseForge page](https://www.curseforge.com/minecraft/modpacks/all-the-mods-10)
   - Find the server files download for the version you want
   - Note the version number and file IDs from the download URL

2. Update using Docker build arguments:
   ```bash
   # Example: Update to ATM10 version 1.16
   docker compose build --build-arg ATM10_VERSION=1.16 \
                        --build-arg ATM10_FILE_ID1=5XXX \
                        --build-arg ATM10_FILE_ID2=XXX \
                        mcserver
   ```

3. Set `OVERWRITE_EXISTING_SERVER=true` in your `.env` file (this will create a fresh world with the new modpack version)

4. Start the server: `./up.sh`

**Note**: The server will detect version changes and notify you in the logs if you try to start without `OVERWRITE_EXISTING_SERVER=true`. Modpack updates often change world generation and mod configurations, so a fresh start is recommended.

## Configuration

Copy `sample.env` to `.env` and modify the following settings:

### Essential Settings
- `SERVER_TYPE`: Server software type - `spigot` for plugin support or `forge` for mod support with ATM10 (default: `spigot`)
- `MINECRAFT_VERSION`: Minecraft version for Spigot (default: 1.21.10). Note: Forge/ATM10 uses a fixed version (1.21.1) determined by the modpack. To update ATM10 to a newer version, see the "Updating ATM10 Version" section below
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
- `JAVA_OPTS`: Java memory settings (e.g., `-Xmx6G -Xms4G` for 6GB max, 4GB initial - increase for Forge/ATM10)

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

For on-demand backups, use the dedicated backup script:

```bash
./backup.sh
```

This creates a timestamped, compressed backup in `./backups/` and provides restoration instructions.

Alternatively, use Docker commands to manually copy server data:

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
