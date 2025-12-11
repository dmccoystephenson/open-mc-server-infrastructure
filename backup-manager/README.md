# Backup Manager

An automated backup management system for the Minecraft server infrastructure. This Spring Boot application runs as a separate container and manages scheduled backups and cleanup.

## Features

- **Scheduled Backups**: Automatically creates backups of the Minecraft server once a day (default: 2 AM)
- **Size Management**: Monitors backup directory size and removes oldest backups when exceeding limit
- **Docker Integration**: Uses Docker commands to create compressed tar.gz backups from the server volume
- **Alert Integration**: Sends notifications to the alert-manager for backup success and failures
- **Configurable**: Customize backup schedule, size limits, and paths via environment variables
- **Containerized**: Runs in its own Docker container with access to Docker socket for backup operations

## Configuration

### Environment Variables

The following environment variables can be configured in `.env`:

- `BACKUP_CONTAINER_NAME`: Container name (default: `open-mc-backup-manager`)
- `BACKUP_MAX_SIZE_MB`: Maximum size of backups directory in MB (default: `10240` = 10GB)
- `BACKUP_SCHEDULE`: Cron expression for backup schedule (default: `0 0 2 * * ?` = 2 AM daily)
- `VOLUME_NAME`: Name of the Docker volume containing Minecraft server data (default: `mcserver`)
- `ALERTS_BACKUP_SUCCESS`: Enable alerts for successful backups (default: `true`)
- `ALERTS_BACKUP_FAILURE`: Enable alerts for failed backups (default: `true`)

### Cron Expression Format

The backup schedule uses standard cron format:
```
second minute hour day-of-month month day-of-week
```

Examples:
- `0 0 2 * * ?` - 2 AM every day (default)
- `0 0 */6 * * ?` - Every 6 hours
- `0 30 1 * * ?` - 1:30 AM every day
- `0 0 0 * * SUN` - Midnight every Sunday

## How It Works

1. **Scheduled Execution**: The backup manager uses Spring's `@Scheduled` annotation to trigger backups
2. **Docker Backup**: Executes Docker commands to create compressed tar.gz backups from the Minecraft server volume
3. **Size Monitoring**: After each backup, checks the total size of the backups directory
4. **Cleanup**: If the directory exceeds the size limit, removes oldest backups first until under limit
5. **Alerts**: Sends notifications to the alert-manager for both successful and failed backup operations

## Building

Build the backup-manager application:

```bash
cd backup-manager
./gradlew clean build
```

## Testing

Run tests:

```bash
./gradlew test
```

## Running

The backup-manager is automatically started with the rest of the infrastructure:

```bash
cd ..
./up.sh
```

### Viewing Logs

To view backup-manager logs:

```bash
docker logs -f open-mc-backup-manager
```

Or use your custom container name:

```bash
docker logs -f ${BACKUP_CONTAINER_NAME}
```

## Manual Backup Trigger

The backup-manager runs backups on a schedule. To create a backup immediately, you can restart the container:

```bash
docker restart open-mc-backup-manager
```

Alternatively, wait for the next scheduled backup time.

## Volume Mounts

The backup-manager container has access to:

- `/mcserver` - Read-only access to the Minecraft server volume
- `/backups` - Read-write access to the backups directory on the host
- `/.env` - Read-only access to environment configuration
- `/var/run/docker.sock` - Docker socket for executing backup operations

## Security Notes

- The container requires access to the Docker socket to run backup commands
- The Minecraft server volume is mounted read-only for safety
- Backups are created using temporary Docker containers with the ubuntu image

## Troubleshooting

### Backups Not Running

1. Check container logs: `docker logs open-mc-backup-manager`
2. Verify the cron expression is correct
3. Ensure the container is running: `docker ps | grep backup-manager`

### Size Limit Not Enforced

1. Check the `BACKUP_MAX_SIZE_MB` setting in `.env`
2. Verify backups are being created with correct naming pattern (`backup-*`)
3. Check container logs for cleanup messages

### Backup Creation Fails

1. Ensure Docker is accessible from within the container
2. Verify the Minecraft server volume exists and is accessible
3. Check that the ubuntu Docker image is available
4. Ensure sufficient disk space for backups

