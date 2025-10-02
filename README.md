# Private Minecraft Server

[![CI Pipeline](https://github.com/dmccoystephenson/private-mc-server/workflows/CI%20Pipeline/badge.svg?branch=main)](https://github.com/dmccoystephenson/private-mc-server/actions)

A Docker-based private Minecraft server running the latest version of Minecraft (1.21.9) with Spigot for enhanced plugin support and performance.

## Features

- **Latest Minecraft Version**: Running Minecraft 1.21.9 with Spigot
- **Docker Containerized**: Easy deployment and management
- **Configurable**: Environment-based configuration
- **Persistent Data**: Server data persists across container restarts
- **Easy Management**: Simple scripts for starting and stopping the server

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

3. **Start the server**
   ```bash
   chmod +x up.sh down.sh
   ./up.sh
   ```
   
   **Note**: The first build will take 10-15 minutes as it downloads and compiles Spigot from source.

4. **Connect to your server**
   - Server address: `localhost:25565` (or your server's IP)
   - The server will take a few minutes to build on first run

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
- `HOST_RCON_PORT`: Host port for RCON (default: `8100`)
- `VOLUME_NAME`: Docker volume name for persistent data (default: `mcserver`)

**Running Parallel Development Servers**: To run multiple servers simultaneously (e.g., for testing different configurations), create separate `.env` files with different values for these settings and use `docker compose --env-file <env-file>` to start each server.

Example for a second server:
```bash
# Create a separate env file for the second server
cp sample.env .env.dev2
# Edit .env.dev2 and change:
# - CONTAINER_NAME=private-mc-server-dev2
# - HOST_PORT=25566
# - HOST_RCON_PORT=8101
# - VOLUME_NAME=mcserver-dev2

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

- Change default operator settings in `.env`
- Consider setting `ONLINE_MODE=true` for authentication
- Don't expose the server publicly without proper security measures
- Regularly backup your world data

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
