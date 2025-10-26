# Backup Manager

An automated backup management system for the Minecraft server infrastructure. This Spring Boot application runs as a separate container and manages scheduled backups and cleanup.

## Features

- **Scheduled Backups**: Automatically runs the `backup.sh` script once a day (default: 2 AM)
- **Size Management**: Monitors backup directory size and removes oldest backups when exceeding limit
- **Configurable**: Customize backup schedule, size limits, and paths via environment variables
- **Containerized**: Runs in its own Docker container with access to Docker socket for backup operations

## Configuration

### Environment Variables

The following environment variables can be configured in `.env`:

- `BACKUP_CONTAINER_NAME`: Container name (default: `open-mc-backup-manager`)
- `BACKUP_MAX_SIZE_MB`: Maximum size of backups directory in MB (default: `10240` = 10GB)
- `BACKUP_SCHEDULE`: Cron expression for backup schedule (default: `0 0 2 * * ?` = 2 AM daily)

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
2. **Backup Script**: Executes the `backup.sh` script which creates timestamped backups
3. **Size Monitoring**: After each backup, checks the total size of the backups directory
4. **Cleanup**: If the directory exceeds the size limit, removes oldest backups first until under limit

## Building

Build the backup-manager application:

```bash
cd backup-manager
./build.sh
```

Or manually:

```bash
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

To trigger a manual backup (useful for testing), you can restart the container or wait for the scheduled time.

For immediate testing, you can modify the schedule temporarily:
1. Update `BACKUP_SCHEDULE` in `.env` to run soon (e.g., `0 */5 * * * ?` for every 5 minutes)
2. Restart the backup-manager container: `docker restart open-mc-backup-manager`

## Volume Mounts

The backup-manager container has access to:

- `/mcserver` - Read-only access to the Minecraft server volume
- `/backups` - Read-write access to the backups directory on the host
- `/backup.sh` - Read-only access to the backup script
- `/.env` - Read-only access to environment configuration
- `/var/run/docker.sock` - Docker socket for executing backup operations

## Security Notes

- The container requires access to the Docker socket to run the backup script
- The Minecraft server volume is mounted read-only for safety
- Only the backup script and its dependencies need write access

## Troubleshooting

### Backups Not Running

1. Check container logs: `docker logs open-mc-backup-manager`
2. Verify the cron expression is correct
3. Ensure the container is running: `docker ps | grep backup-manager`

### Size Limit Not Enforced

1. Check the `BACKUP_MAX_SIZE_MB` setting in `.env`
2. Verify backups are being created with correct naming pattern (`backup-*`)
3. Check container logs for cleanup messages

### Backup Script Fails

1. Ensure Docker is accessible from within the container
2. Verify the Minecraft server volume exists and is accessible
3. Check that the `backup.sh` script has correct permissions
