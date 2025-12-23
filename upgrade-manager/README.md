# Upgrade Manager

A Spring Boot service that manages Minecraft server version upgrades via REST API.

## Overview

The Upgrade Manager replaces the legacy `upgrade.sh` shell script with a robust, containerized Java application that handles server upgrades through a REST API. It integrates with the backup-manager and alert-manager services to ensure safe and monitored upgrades.

## Features

- **REST API**: Trigger upgrades via HTTP POST requests
- **Automated Backup**: Ensures a backup exists before upgrading
- **Version Management**: Updates `.env` file with new Minecraft version
- **Docker Integration**: Rebuilds Docker images and manages container lifecycle
- **Alert Integration**: Sends notifications to alert-manager during upgrade process
- **Safe Upgrade Process**: Follows best practices with backup verification and graceful shutdown

## API Endpoints

### Trigger Upgrade

Initiates a server upgrade to a new Minecraft version.

**Endpoint:** `POST /api/upgrades/trigger`

**Request Body:**
```json
{
  "newVersion": "1.21.10"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Successfully upgraded from 1.20.0 to 1.21.10",
  "previousVersion": "1.20.0",
  "newVersion": "1.21.10",
  "backupPath": "/backups/backup-20240101-120000"
}
```

**Error Response (400 Bad Request):**
```json
{
  "success": false,
  "message": "New version is required",
  "error": "Missing or empty version parameter"
}
```

**Error Response (500 Internal Server Error):**
```json
{
  "success": false,
  "message": "Upgrade failed",
  "error": "Docker build failed with exit code: 1"
}
```

## Usage

⚠️ **Security Warning**: The upgrade endpoint performs administrative actions (stops server, modifies `.env`, rebuilds Docker images, accesses Docker socket) and **must not be exposed publicly**. Ensure it is only reachable from a trusted admin network/interface. For production deployments, implement authentication (e.g., via Spring Security with API tokens or a reverse proxy with authentication).

### Using curl

```bash
curl -X POST http://localhost:8092/api/upgrades/trigger \
  -H "Content-Type: application/json" \
  -d '{"newVersion": "1.21.10"}'
```

### Using the trigger script

A convenience script is provided for triggering upgrades:

```bash
./trigger-upgrade.sh 1.21.10
```

## Configuration

The upgrade-manager is configured through environment variables in the `.env` file:

### Required Configuration

- `MINECRAFT_VERSION`: Current Minecraft version (will be updated during upgrade)

### Optional Configuration

- `UPGRADE_CONTAINER_NAME`: Container name (default: `open-mc-upgrade-manager`)
- `UPGRADE_PORT`: API port (default: `8092`)
- `CONTAINER_NAME`: Minecraft server container name (default: `open-mc-server`)
- `ALERTS_UPGRADE_START`: Send alert when upgrade starts (default: `true`)
- `ALERTS_UPGRADE_COMPLETE`: Send alert when upgrade completes (default: `true`)
- `ALERTS_UPGRADE_FAILURE`: Send alert when upgrade fails (default: `true`)

## Upgrade Process

The upgrade manager follows these steps:

1. **Backup Verification**: Checks for existing backup or creates a new one via backup-manager API
2. **Server Shutdown**: Gracefully stops the Minecraft server using `docker compose stop mcserver`
3. **Version Update**: Updates `MINECRAFT_VERSION` in `.env` file
4. **Docker Rebuild**: Rebuilds the Docker image with the new Minecraft version
5. **Server Startup**: Starts the server with the new version using `docker compose start mcserver`
6. **Monitoring**: Checks server logs to verify successful startup

**Note**: The upgrade process uses `docker compose stop/start` commands to control only the mcserver service, ensuring that other services (including upgrade-manager itself) remain running during the upgrade.

## Integration with Other Services

### Backup Manager

The upgrade-manager calls the backup-manager API to ensure a backup exists before upgrading:

```
POST http://backup-manager:8091/api/backups/trigger
```

### Alert Manager

The upgrade-manager sends alerts to the alert-manager during the upgrade process:

```
POST http://alert-manager:8090/api/alerts
```

Alerts are sent for:
- Upgrade start
- Upgrade completion
- Upgrade failure

## Development

### Building

```bash
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

### Running Locally

```bash
./gradlew bootRun
```

### Docker Build

```bash
docker build -t upgrade-manager .
```

## Dependencies

- Spring Boot 3.2.0
- Java 21
- Docker CLI (for container management)
- Backup Manager service
- Alert Manager service

## Architecture

The upgrade-manager follows the same architectural patterns as other services in the infrastructure:

- **Controller Layer**: REST API endpoints (`UpgradeController`)
- **Service Layer**: Business logic (`UpgradeService`)
- **Model Layer**: Request/Response DTOs (`UpgradeRequest`, `UpgradeResponse`)
- **Exception Handling**: Custom exceptions (`UpgradeException`)
- **Configuration**: Spring Boot configuration and RestTemplate setup

## Testing

The module includes comprehensive unit tests:

- `UpgradeServiceTest`: Tests for service layer logic
- `UpgradeControllerTest`: Tests for REST API endpoints
- `UpgradeManagerApplicationTest`: Application context loading test

Run tests with:
```bash
./gradlew test
```

## Troubleshooting

### Upgrade Fails to Start

- Check that backup-manager is running and accessible
- Verify the `.env` file exists and is readable
- Check Docker daemon is running and accessible

### Docker Build Fails

- Ensure sufficient disk space for building Spigot
- Check network connectivity for downloading dependencies
- Review Docker logs for specific build errors

### Server Won't Start After Upgrade

- Check the backup was created successfully
- Review server logs: `docker logs open-mc-server`
- Consider rolling back using the backup (see UPGRADE-GUIDE.md)

## Security Considerations

⚠️ **Critical**: The upgrade-manager API provides administrative control over the server and must be properly secured.

- The upgrade-manager requires access to:
  - Docker socket (for container management)
  - `.env` file (for version updates)
  - File system (for backup verification)
- **The API must not be publicly exposed without authentication**
- Anyone who can access the API endpoint can:
  - Stop and restart the server
  - Modify the `.env` configuration file
  - Trigger Docker builds
  - Indirectly access the Docker daemon
- For production deployments:
  - Implement authentication/authorization (e.g., Spring Security with API tokens)
  - Use a reverse proxy with authentication
  - Restrict network access to trusted admin networks only
  - Consider using VPN or SSH tunneling for remote access

## Future Enhancements

Potential improvements for future versions:

- Authentication and authorization
- Rollback API endpoint
- Upgrade scheduling
- Pre-upgrade validation checks
- Post-upgrade verification tests
- Support for plugin compatibility checks
