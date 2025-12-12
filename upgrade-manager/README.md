# Upgrade Manager

A Spring Boot service for managing Minecraft server upgrades and version checking.

## Features

- **REST API for Triggering Upgrades**: Trigger server upgrades via HTTP POST request
- **Automatic Version Checking**: Periodically checks for new Minecraft versions and sends alerts
- **Safe Upgrade Process**: Ensures backups exist before upgrading
- **Alert Integration**: Sends alerts for upgrade status and version updates
- **Docker Integration**: Manages Docker containers and images during upgrades

## API Endpoints

### Upgrade Operations

#### Trigger Upgrade
```bash
POST /api/upgrade/trigger
Content-Type: application/json

{
  "version": "1.21.11"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Upgrade completed successfully",
  "previousVersion": "1.21.10",
  "newVersion": "1.21.11",
  "backupPath": "/backups/backup-20231212-120000"
}
```

### Version Checking

#### Get Current Version
```bash
GET /api/version/current
```

**Response:**
```json
{
  "version": "1.21.10",
  "success": true
}
```

#### Get Latest Version
```bash
GET /api/version/latest
```

**Response:**
```json
{
  "version": "1.21.11",
  "success": true
}
```

#### Check Version Status
```bash
GET /api/version/check
```

**Response:**
```json
{
  "currentVersion": "1.21.10",
  "latestVersion": "1.21.11",
  "outdated": true,
  "message": "Server is outdated",
  "success": true
}
```

## Configuration

Configuration is managed through environment variables defined in `.env`:

| Variable | Default | Description |
|----------|---------|-------------|
| `UPGRADE_PORT` | `8092` | Port for the upgrade manager API |
| `UPGRADE_CONTAINER_NAME` | `open-mc-upgrade-manager` | Container name |
| `VERSION_CHECK_ENABLED` | `true` | Enable automatic version checking |
| `VERSION_CHECK_SCHEDULE` | `0 0 3 * * ?` | Cron schedule for version checks (3 AM daily) |
| `ALERTS_UPGRADE_START` | `true` | Send alert when upgrade starts |
| `ALERTS_UPGRADE_COMPLETE` | `true` | Send alert when upgrade completes |
| `ALERTS_UPGRADE_FAILURE` | `true` | Send alert when upgrade fails |
| `ALERTS_VERSION_CHECK` | `true` | Send alert when version is outdated |

## Upgrade Process

When an upgrade is triggered, the following steps are performed:

1. **Backup Check**: Ensures a valid backup exists (or creates one)
2. **Server Stop**: Stops the Minecraft server gracefully
3. **Version Update**: Updates `MINECRAFT_VERSION` in `.env` file
4. **Image Rebuild**: Rebuilds the Docker image with the new version
5. **Server Start**: Starts the server with the new version
6. **Monitoring**: Monitors the server startup and logs

## Development

### Building

```bash
./gradlew build
```

### Testing

```bash
./gradlew test
```

### Running Locally

```bash
./gradlew bootRun
```

## Integration

The upgrade manager integrates with:

- **Backup Manager**: Triggers backups before upgrades
- **Alert Manager**: Sends notifications about upgrade status
- **Docker**: Manages container lifecycle and image building

## Notes

- The upgrade manager requires access to the Docker socket to manage containers
- Upgrades can take 10-15 minutes as they compile Spigot from source
- Always ensure a backup exists before triggering an upgrade
- The service automatically checks for version updates daily at 3 AM (configurable)
