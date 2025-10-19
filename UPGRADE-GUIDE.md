# Minecraft Server Upgrade Guide

This guide provides a comprehensive process for upgrading your Minecraft server to a newer version while ensuring data safety and the ability to rollback if needed.

## Table of Contents

- [Before You Begin](#before-you-begin)
- [Upgrade Process](#upgrade-process)
- [Rollback Procedure](#rollback-procedure)
- [Post-Upgrade Verification](#post-upgrade-verification)
- [Troubleshooting](#troubleshooting)

## Before You Begin

### Important Notes

⚠️ **Critical**: Always backup your server data before performing an upgrade. This allows you to restore your server if the upgrade fails or causes issues.

⚠️ **Compatibility**: Check that your installed plugins are compatible with the new Minecraft version. Incompatible plugins may cause server crashes or data corruption.

⚠️ **Downtime**: The upgrade process requires server downtime. Plan the upgrade during a maintenance window and notify your players in advance.

### Prerequisites

- Docker and Docker Compose installed and running
- Access to the server host machine
- Sufficient disk space for backups (at least 2x your current world size)
- Knowledge of the target Minecraft version you want to upgrade to

## Upgrade Process

### Automated Upgrade (Recommended)

For a streamlined upgrade experience, use the automated upgrade script that handles all steps:

```bash
./upgrade.sh
```

The script will:
1. ✅ Stop the server gracefully
2. ✅ Create a timestamped backup automatically
3. ✅ Prompt for the new Minecraft version
4. ✅ Update the `.env` file
5. ✅ Rebuild the Docker image with the new version
6. ✅ Start the server and show initial logs

**Benefits:**
- Single command execution
- Automatic backup management
- Interactive prompts with confirmation
- Progress feedback at each step
- Summary with backup location

**Example usage:**
```bash
./upgrade.sh
# When prompted, enter the new version (e.g., 1.21.10)
# Confirm the upgrade when asked
# Script handles the rest automatically
```

If you prefer manual control or need to understand each step, continue with the manual process below.

---

### Manual Upgrade Process

### Step 1: Stop the Server

First, gracefully stop the Minecraft server to ensure all data is properly saved:

```bash
./down.sh
```

Or using Docker Compose directly:

```bash
docker compose down
```

**Important**: The server includes graceful shutdown handling that automatically saves all data before stopping.

### Step 2: Backup Server Files

Create a backup of all server data from the persistent volume. This is your safety net in case something goes wrong.

#### Option A: Backup to Local Directory (Recommended)

```bash
# Create a backup directory with timestamp
mkdir -p ./backups
BACKUP_DIR="./backups/backup-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"

# Copy server data from the container volume
docker run --rm \
  -v mcserver:/mcserver:ro \
  -v "$(pwd)/$BACKUP_DIR":/backup \
  ubuntu \
  tar czf /backup/mcserver-backup.tar.gz -C /mcserver .

echo "Backup created at: $BACKUP_DIR/mcserver-backup.tar.gz"
```

#### Option B: Quick Backup via Docker CP

```bash
# Create backup directory
mkdir -p ./backups/backup-$(date +%Y%m%d-%H%M%S)

# Start a temporary container to access the volume
docker run -d --name mcserver-backup \
  -v mcserver:/mcserver:ro \
  ubuntu sleep 300

# Copy the data
docker cp mcserver-backup:/mcserver "./backups/backup-$(date +%Y%m%d-%H%M%S)/"

# Cleanup temporary container
docker rm -f mcserver-backup
```

#### Option C: Using Deposit Box

```bash
# Copy world and important files to deposit box
docker run --rm \
  -v mcserver:/mcserver:ro \
  -v "$(pwd)/deposit-box":/deposit-box \
  ubuntu \
  bash -c "cp -r /mcserver/world /deposit-box/ && \
           cp -r /mcserver/plugins /deposit-box/ && \
           cp /mcserver/ops.json /deposit-box/ 2>/dev/null || true && \
           cp /mcserver/whitelist.json /deposit-box/ 2>/dev/null || true"
```

**Verify your backup** before proceeding:

```bash
# For Option A
ls -lh "$BACKUP_DIR/mcserver-backup.tar.gz"

# For Option B
du -sh "./backups/backup-$(date +%Y%m%d-%H%M%S)/"
```

### Step 3: Update Minecraft Version

Edit your `.env` file to specify the new Minecraft version:

```bash
# Open .env in your preferred editor
nano .env
# or
vim .env
```

Update the `MINECRAFT_VERSION` variable:

```bash
# Change from (example):
MINECRAFT_VERSION=1.21.9

# To your target version (example):
MINECRAFT_VERSION=1.21.10
```

**Note**: The version must match an available Spigot build. Check [Spigot BuildTools](https://www.spigotmc.org/wiki/buildtools/) for supported versions.

**Important**: The Dockerfile now uses a build argument that automatically reads the `MINECRAFT_VERSION` from your `.env` file, so you only need to update the version in one place!

### Step 4: Rebuild Docker Image

Rebuild the Docker image with the new Minecraft version. This process will:
- Download and compile the new Spigot version
- Create a new server JAR file
- Take 10-15 minutes depending on your system

```bash
docker compose build --no-cache
```

**Note**: The `--no-cache` flag ensures a clean build without using old cached layers.

### Step 5: Replace Server JAR in Persistent Volume

The new server JAR needs to be placed in the persistent volume. The `post-create.sh` script handles this automatically based on the `OVERWRITE_EXISTING_SERVER` setting.

#### Option A: Preserve Existing World (Recommended)

If you want to keep your existing world and only update the server JAR:

```bash
# The server setup script will detect the new version and copy only the JAR
# Your world data remains intact
./up.sh
```

The `setup_server()` function in `post-create.sh` will:
- Detect existing server files
- Copy the new JAR if the version changed
- Preserve your world data, plugins, and configurations

#### Option B: Fresh Server Setup

If you want to completely reset the server (⚠️ This will delete your world):

```bash
# Edit .env to enable overwrite
echo "OVERWRITE_EXISTING_SERVER=true" >> .env

# Start the server (this will reset everything)
./up.sh

# After the server starts, disable overwrite for future restarts
sed -i 's/OVERWRITE_EXISTING_SERVER=true/OVERWRITE_EXISTING_SERVER=false/' .env
```

### Step 6: Start and Monitor the Server

Start the server and monitor the logs to ensure successful startup:

```bash
# Start the server
./up.sh

# Monitor server logs in real-time
docker logs -f private-mc-server
```

**Look for these indicators of successful startup:**
- `[SERVER-SETUP] Starting server with graceful shutdown wrapper...`
- `Done (X.XXXs)! For help, type "help"`
- No errors about incompatible world format
- Plugins loading successfully

Press `Ctrl+C` to stop following the logs (server continues running).

## Rollback Procedure

If the upgrade fails or causes issues, you can restore your server from the backup.

### Step 1: Stop the Server

```bash
./down.sh
```

### Step 2: Remove Current Server Volume

**Warning**: This will delete the current server data. Make sure you have a backup!

```bash
# Remove the volume
docker volume rm mcserver
```

### Step 3: Restore from Backup

#### If you used Option A (tar.gz backup):

```bash
# Specify your backup file
BACKUP_FILE="./backups/backup-YYYYMMDD-HHMMSS/mcserver-backup.tar.gz"

# Restore the data
docker run --rm \
  -v mcserver:/mcserver \
  -v "$(pwd)/$(dirname $BACKUP_FILE)":/backup \
  ubuntu \
  tar xzf /backup/$(basename $BACKUP_FILE) -C /mcserver
```

#### If you used Option B (directory backup):

```bash
# Specify your backup directory
BACKUP_DIR="./backups/backup-YYYYMMDD-HHMMSS"

# Start a temporary container
docker run -d --name mcserver-restore \
  -v mcserver:/mcserver \
  ubuntu sleep 300

# Copy the data back
docker cp "$BACKUP_DIR/mcserver/." mcserver-restore:/mcserver/

# Cleanup
docker rm -f mcserver-restore
```

### Step 4: Revert Configuration

Restore your previous `.env` and `Dockerfile` settings:

```bash
# Edit .env to restore old MINECRAFT_VERSION
nano .env

# Edit Dockerfile to restore old version references
nano Dockerfile
```

### Step 5: Rebuild and Restart

```bash
# Rebuild with the old version
docker compose build --no-cache

# Start the server
./up.sh
```

## Post-Upgrade Verification

After upgrading, verify that everything is working correctly:

### 1. Check Server Status

```bash
# Verify the container is running
docker ps | grep private-mc-server

# Check server logs
docker logs private-mc-server --tail 50
```

### 2. Test Server Connection

- Connect to the server using your Minecraft client
- Verify the server version matches your upgrade target
- Check the F3 debug screen (press F3) to see the server version

### 3. Verify World Data

- Check that your world loaded correctly
- Verify builds and structures are intact
- Test chunk loading and generation

### 4. Check Plugins

```bash
# Access server console
docker exec -it private-mc-server screen -r minecraft
# Use 'plugins' command to list all plugins
# Press Ctrl+A then D to detach

# Or view from logs
docker logs private-mc-server | grep -i plugin
```

Verify that:
- All expected plugins are loaded
- No plugin errors in the logs
- Plugin functionality works in-game

### 5. Test Core Functionality

- Player movement and interaction
- Block breaking and placing
- Inventory management
- Commands and permissions
- Chat and multiplayer features

### 6. Monitor Performance

Monitor resource usage to ensure the upgrade hasn't introduced performance issues:

```bash
# Quick resource check
docker stats private-mc-server

# Detailed monitoring with bottleneck analysis (recommended)
./monitor.sh -i 10 -d 300 -l post-upgrade-$(date +%Y%m%d).log

# Analyze the results
./monitor.sh -a post-upgrade-$(date +%Y%m%d).log

# Watch for errors
docker logs -f private-mc-server | grep -i error
```

**Post-upgrade monitoring checklist:**
- CPU usage should remain within normal ranges (<80%)
- Memory usage should not increase significantly
- Network I/O should be consistent with pre-upgrade levels
- No memory leaks over extended periods

Compare results with pre-upgrade baselines if available. If resource usage increased significantly, check for:
- New resource-intensive features in the Minecraft version
- Plugin compatibility issues causing performance degradation
- Configuration settings that need adjustment for the new version

## Troubleshooting

### Server Fails to Start After Upgrade

**Symptoms**: Container stops immediately or crashes on startup

**Solutions**:
1. Check the logs for specific errors:
   ```bash
   docker logs private-mc-server
   ```
2. Common issues:
   - Incompatible world format: Rollback to previous version
   - Incompatible plugins: Remove or update plugins
   - Insufficient memory: Adjust `JAVA_OPTS` in environment

### "Outdated Server" or "Outdated Client" Error

**Symptoms**: Players cannot connect, version mismatch errors

**Solutions**:
1. Verify the server is running the correct version:
   ```bash
   docker logs private-mc-server | grep "Starting minecraft server version"
   ```
2. Ensure the Dockerfile version matches `.env`
3. Rebuild the image if versions don't match:
   ```bash
   docker compose build --no-cache
   ```

### World Data Missing or Corrupted

**Symptoms**: Empty world, missing builds, or world won't load

**Solutions**:
1. Immediately stop the server:
   ```bash
   ./down.sh
   ```
2. Follow the [Rollback Procedure](#rollback-procedure)
3. Do not start the server until the rollback is complete

### Plugins Not Loading

**Symptoms**: Plugin features not working, plugin errors in logs

**Solutions**:
1. Check plugin compatibility with the new Minecraft version
2. Update plugins to compatible versions:
   ```bash
   # Copy updated plugin JARs to deposit-box
   docker exec private-mc-server bash -c "cp /deposit-box/*.jar /mcserver/plugins/"
   docker compose restart
   ```
3. Remove incompatible plugins and restart

### Performance Degradation

**Symptoms**: Lag, low TPS (ticks per second), high CPU/memory usage

**Solutions**:
1. Identify bottlenecks with monitoring:
   ```bash
   # Monitor for 5 minutes during peak load
   ./monitor.sh -i 10 -d 300 -l performance-check.log
   
   # Analyze for bottlenecks
   ./monitor.sh -a performance-check.log
   ```
2. Quick resource check:
   ```bash
   docker stats private-mc-server
   ```
3. Based on bottleneck analysis:
   
   **If CPU is the bottleneck (>80% usage):**
   - Reduce view-distance in server.properties (e.g., from 10 to 8)
   - Reduce simulation-distance (e.g., from 10 to 6)
   - Limit entities and mob farms
   - Remove resource-intensive plugins
   - Upgrade to a VM with more CPU cores
   
   **If Memory is the bottleneck (>85% usage):**
   - Increase JAVA_OPTS memory allocation:
     ```yaml
     environment:
       - JAVA_OPTS=-Xmx4G -Xms2G  # Increase both values
     ```
   - Upgrade VM RAM
   - Reduce loaded chunks (lower view-distance)
   - Check for memory leaks in plugins
   
4. Monitor again after changes to verify improvements

### Docker Volume Issues

**Symptoms**: "Cannot start service" or volume mount errors

**Solutions**:
1. Verify the volume exists:
   ```bash
   docker volume ls | grep mcserver
   ```
2. Inspect volume details:
   ```bash
   docker volume inspect mcserver
   ```
3. If corrupted, restore from backup (see [Rollback Procedure](#rollback-procedure))

## Additional Resources

- [Spigot BuildTools Documentation](https://www.spigotmc.org/wiki/buildtools/)
- [Minecraft Version History](https://minecraft.fandom.com/wiki/Java_Edition_version_history)
- [Plugin Compatibility Checker](https://www.spigotmc.org/)
- [Server Configuration Guide](./README.md#configuration)

## Support

If you encounter issues not covered in this guide:

1. Check the [main README troubleshooting section](./README.md#troubleshooting)
2. Review server logs for specific error messages
3. Submit an issue on the [GitHub repository](https://github.com/dmccoystephenson/private-mc-server/issues)

---

**Remember**: Always backup before upgrading, test in a non-production environment when possible, and have a rollback plan ready.
