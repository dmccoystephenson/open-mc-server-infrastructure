#!/bin/bash
set -euo pipefail

# Minecraft Server Upgrade Script
# This script automates the upgrade process for the Minecraft server
# including backup management and version updates.
#
# Usage:
#   ./upgrade.sh                  Interactive upgrade (prompts for version)
#   ./upgrade.sh --dry-run        Show the upgrade plan without making changes
#   ./upgrade.sh --dry-run 26.2   Show the plan for a specific target version

cd "$(dirname "$0")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored messages
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to send an alert to the alert-manager
send_alert() {
    local title="$1"
    local message="$2"
    local level="${3:-INFO}"
    local source="upgrade-script"
    local alert_toggle="${4:-}"
    
    # Check if this type of alert is enabled (if toggle variable is provided)
    if [ -n "$alert_toggle" ]; then
        local toggle_value="${!alert_toggle:-true}"
        if [ "$toggle_value" != "true" ]; then
            log_info "Alert skipped (disabled via $alert_toggle): $title"
            return 0
        fi
    fi
    
    # Determine the alert manager URL based on environment
    local alert_url
    if [ -n "${ALERT_MANAGER_URL:-}" ]; then
        alert_url="$ALERT_MANAGER_URL"
    elif [ -f /.dockerenv ] || grep -q docker /proc/1/cgroup 2>/dev/null; then
        alert_url="http://alert-manager:8090/api/alerts"
    else
        alert_url="http://localhost:8090/api/alerts"
    fi
    
    # Try to send alert, but don't fail if it doesn't work
    if command -v curl >/dev/null 2>&1; then
        curl -X POST "$alert_url" \
          -H "Content-Type: application/json" \
          --max-time 5 \
          --connect-timeout 5 \
          -d "{\"title\":\"$title\",\"message\":\"$message\",\"level\":\"$level\",\"source\":\"$source\"}" \
          >/dev/null 2>&1 || true
    fi
}


# Function to load env value
get_env_value() {
    local key=$1
    local default=$2
    if [ -f .env ]; then
        grep "^${key}=" .env | cut -d'=' -f2 || echo "$default"
    else
        echo "$default"
    fi
}

# The backups and mcserver volumes are named Docker volumes (not host bind
# mounts), so backup discovery/inspection has to go through a throwaway
# container rather than reading a "./backups" path on the host.

# List backup-* directory names in a named volume, newest first. Backup
# directory names are zero-padded "backup-yyyyMMdd-HHmmss", so a reverse
# lexicographic sort is also a reverse chronological sort.
list_backup_dirs() {
    local volume=$1
    docker run --rm -v "${volume}:/backups:ro" alpine sh -c '
        for d in /backups/backup-*/; do
            [ -d "$d" ] && basename "$d"
        done
    ' 2>/dev/null | sort -r
}

# True (exit 0) if the given backup directory contains a completed archive.
backup_is_complete() {
    local volume=$1
    local name=$2
    docker run --rm -v "${volume}:/backups:ro" alpine sh -c \
        'test -f "/backups/$1/mcserver-backup.tar.gz"' _ "$name" >/dev/null 2>&1
}

# Age of a backup directory in seconds, based on its mtime.
backup_age_seconds() {
    local volume=$1
    local name=$2
    local mtime
    mtime=$(docker run --rm -v "${volume}:/backups:ro" alpine sh -c \
        'stat -c %Y "/backups/$1"' _ "$name" 2>/dev/null) || return 1
    echo $(( $(date +%s) - mtime ))
}

# Human-readable disk usage for a path inside a named volume (default: the
# whole volume). Returns "unknown" if it can't be determined.
volume_disk_usage() {
    local volume=$1
    local subpath="${2:-}"
    local out
    out=$(docker run --rm -v "${volume}:/vol:ro" alpine sh -c \
        'du -sh "/vol$1" 2>/dev/null' _ "$subpath") || { echo "unknown"; return; }
    echo "$out" | awk '{print $1}'
}

# Poll `docker logs` for a bounded time looking for a startup success/failure
# indicator. Returns 0 on confirmed success, 1 on confirmed failure, 2 if
# inconclusive within max_wait.
wait_for_server_start() {
    local container_name=$1
    local max_wait="${2:-180}"
    local interval=5
    local elapsed=0
    local logs

    while [ "$elapsed" -lt "$max_wait" ]; do
        logs=$(docker logs "$container_name" --tail 200 2>&1 || true)
        if echo "$logs" | grep -qiE 'Done \([0-9.]+s\)! For help'; then
            return 0
        fi
        if echo "$logs" | grep -qiE 'failed to bind|exception in server tick loop|could not load|corrupt(ed)? world'; then
            return 1
        fi
        sleep "$interval"
        elapsed=$((elapsed + interval))
    done
    return 2
}

# Function to check if server is running
is_server_running() {
    local container_name
    container_name=$(get_env_value "CONTAINER_NAME" "open-mc-server")
    docker ps --format '{{.Names}}' | grep -q "^${container_name}$"
}

# Function to get current version from .env
get_current_version() {
    if [ -f .env ]; then
        grep "^MINECRAFT_VERSION=" .env | cut -d'=' -f2
    else
        echo "unknown"
    fi
}

# Function to update version in .env file
update_env_version() {
    local new_version=$1
    
    if [ ! -f .env ]; then
        log_error ".env file not found! Please create it from sample.env first."
        return 1
    fi
    
    # Update MINECRAFT_VERSION in .env
    if grep -q "^MINECRAFT_VERSION=" .env; then
        sed -i "s/^MINECRAFT_VERSION=.*/MINECRAFT_VERSION=$new_version/" .env
        log_success "Updated MINECRAFT_VERSION to $new_version in .env"
    else
        echo "MINECRAFT_VERSION=$new_version" >> .env
        log_success "Added MINECRAFT_VERSION=$new_version to .env"
    fi
}

print_usage() {
    cat <<'EOF'
Usage: ./upgrade.sh [--dry-run] [VERSION]

  --dry-run   Show the current version, target version, world disk usage,
              and planned backup location without making any changes.
  VERSION     Optional target Minecraft version. Prompted for interactively
              if omitted.
EOF
}

# Show the upgrade plan without touching the server, backups, or .env.
run_dry_run() {
    local current_version=$1
    local target_version=$2
    local backups_volume=$3
    local mcserver_volume=$4

    if [ -z "$target_version" ]; then
        read -r -p "Enter the new Minecraft version (e.g., 1.21.10): " target_version
    fi
    if [ -z "$target_version" ]; then
        log_error "No version specified. Aborting dry run."
        exit 1
    fi

    echo ""
    echo "=========================================="
    echo "  Dry Run — no changes will be made"
    echo "=========================================="
    echo ""
    log_info "Current Minecraft version: $current_version"
    log_info "Target Minecraft version:  $target_version"

    local world_usage
    world_usage=$(volume_disk_usage "$mcserver_volume")
    log_info "Current world data size (volume '$mcserver_volume'): $world_usage"

    local latest_backup
    latest_backup=$(list_backup_dirs "$backups_volume" | head -1)
    if [ -n "$latest_backup" ] && backup_is_complete "$backups_volume" "$latest_backup"; then
        log_info "Existing backup would be reused: $latest_backup (volume '$backups_volume')"
    else
        log_info "No valid existing backup found — a new one would be created via trigger-backup.sh"
    fi
    log_info "Planned backup location: volume '$backups_volume', directory backup-<timestamp>/mcserver-backup.tar.gz"

    echo ""
    log_info "No changes were made (dry run)."
}

# Main upgrade process
main() {
    echo "=========================================="
    echo "  Minecraft Server Upgrade Script"
    echo "=========================================="
    echo ""
    
    # Check if .env exists
    if [ ! -f .env ]; then
        log_error ".env file not found!"
        log_info "Please create .env from sample.env before running this script."
        log_info "Run: cp sample.env .env"
        exit 1
    fi
    
    # Get current version
    current_version=$(get_current_version)

    local backups_volume mcserver_volume
    backups_volume=$(get_env_value "BACKUPS_VOLUME_NAME" "backups")
    mcserver_volume=$(get_env_value "VOLUME_NAME" "mcserver")

    if [ "$DRY_RUN" = true ]; then
        run_dry_run "$current_version" "$TARGET_VERSION_ARG" "$backups_volume" "$mcserver_volume"
        exit 0
    fi

    log_info "Current Minecraft version: $current_version"
    echo ""

    # Prompt for new version
    local new_version="$TARGET_VERSION_ARG"
    if [ -z "$new_version" ]; then
        read -r -p "Enter the new Minecraft version (e.g., 1.21.10): " new_version
    fi

    if [ -z "$new_version" ]; then
        log_error "No version specified. Aborting."
        exit 1
    fi
    
    # Confirm upgrade
    echo ""
    log_warning "This will upgrade your server from $current_version to $new_version"
    read -r -p "Do you want to continue? (yes/no): " confirm
    
    if [ "$confirm" != "yes" ] && [ "$confirm" != "y" ]; then
        log_info "Upgrade cancelled."
        exit 0
    fi
    
    echo ""
    log_info "Starting upgrade process..."
    send_alert "Server Upgrade Started" "Starting upgrade from $current_version to $new_version" "INFO" "ALERTS_UPGRADE_START"
    echo ""
    
    # Step 1: Ensure backup exists
    log_info "Step 1/6: Checking for backup..."
    
    # Backups live in the named "backups" Docker volume, so discovery goes
    # through a throwaway container rather than a host "./backups" path.
    backup_dir=$(list_backup_dirs "$backups_volume" | head -1)

    if [ -n "$backup_dir" ] && backup_is_complete "$backups_volume" "$backup_dir"; then
        local age
        age=$(backup_age_seconds "$backups_volume" "$backup_dir" || echo 999999)
        if [ "$age" -le 3600 ]; then
            log_info "Using recent backup: $backup_dir"
        else
            log_info "Using existing backup: $backup_dir"
        fi
    else
        log_warning "No valid backup found. Creating a new backup..."

        # Check if trigger-backup.sh exists
        if [ ! -f ./trigger-backup.sh ]; then
            log_error "trigger-backup.sh script not found!"
            log_info "Please ensure trigger-backup.sh is available to create a backup."
            exit 1
        fi

        # Trigger a new backup (while services are still running)
        log_info "Running trigger-backup.sh to create a backup before upgrade..."
        echo ""
        if ! ./trigger-backup.sh; then
            log_error "Backup creation failed! Cannot proceed with upgrade without a backup."
            exit 1
        fi
        echo ""

        # Get the most recent backup that was just created
        backup_dir=$(list_backup_dirs "$backups_volume" | head -1)

        if [ -z "$backup_dir" ] || ! backup_is_complete "$backups_volume" "$backup_dir"; then
            log_error "Backup creation succeeded but backup file not found!"
            exit 1
        fi

        log_success "Backup created successfully: $backup_dir"
    fi

    log_success "Backup verified: $backup_dir (volume '$backups_volume')"
    echo ""
    
    # Step 2: Stop the server
    log_info "Step 2/6: Stopping the server..."
    if is_server_running; then
        ./down.sh
        log_success "Server stopped successfully"
    else
        log_info "Server is not running, skipping stop step"
    fi
    echo ""
    
    # Step 3: Update version in .env
    log_info "Step 3/6: Updating MINECRAFT_VERSION in .env..."
    update_env_version "$new_version"
    echo ""
    
    # Step 4: Rebuild Docker image
    log_info "Step 4/6: Rebuilding Docker image with new version..."
    log_warning "This may take 10-15 minutes as it compiles Spigot from source..."
    docker compose build --no-cache 2>&1 | while IFS= read -r line; do
        echo "  $line"
    done
    
    if [ "${PIPESTATUS[0]}" -eq 0 ]; then
        log_success "Docker image rebuilt successfully"
    else
        log_error "Docker build failed!"
        log_warning "Your backup is available at: $backup_dir"
        send_alert "Server Upgrade Failed" "Upgrade from $current_version to $new_version failed during Docker build. Backup: $backup_dir" "ERROR" "ALERTS_UPGRADE_FAILURE"
        exit 1
    fi
    echo ""
    
    # Step 5: Start the server
    log_info "Step 5/6: Starting the server..."
    ./up.sh
    log_success "Server started"
    echo ""
    
    # Step 6: Monitor startup
    log_info "Step 6/6: Monitoring server startup..."

    local container_name
    container_name=$(get_env_value "CONTAINER_NAME" "open-mc-server")

    local start_status=0
    wait_for_server_start "$container_name" 180 || start_status=$?

    # Show recent logs
    echo ""
    log_info "Recent server logs:"
    echo "----------------------------------------"
    docker logs "$container_name" --tail 20 2>&1 || log_warning "Could not retrieve logs"
    echo "----------------------------------------"
    echo ""

    # Final summary
    echo "=========================================="
    if [ "$start_status" -eq 1 ]; then
        log_error "Server logs indicate the upgrade may have failed to start!"
        send_alert "Server Upgrade Failed" "Upgrade from $current_version to $new_version rebuilt and started, but logs indicate a startup failure. Backup: $backup_dir" "ERROR" "ALERTS_UPGRADE_FAILURE"
    elif [ "$start_status" -eq 2 ]; then
        log_warning "Could not confirm successful startup from logs within 180s — check manually."
        send_alert "Server Upgrade Unverified" "Upgrade from $current_version to $new_version rebuilt and started, but startup could not be confirmed from logs. Backup: $backup_dir" "WARNING" "ALERTS_UPGRADE_COMPLETE"
    else
        log_success "Upgrade completed successfully!"
        send_alert "Server Upgrade Complete" "Successfully upgraded from $current_version to $new_version. Backup: $backup_dir" "INFO" "ALERTS_UPGRADE_COMPLETE"
    fi
    echo "=========================================="
    echo ""
    log_info "Summary:"
    echo "  - Previous version: $current_version"
    echo "  - New version: $new_version"
    echo "  - Backup location: $backup_dir (volume '$backups_volume')"
    echo ""
    log_info "Next steps:"
    echo "  1. Monitor logs: docker logs -f $container_name"
    echo "  2. Connect to the server and verify everything works"
    echo "  3. Check that plugins are compatible with the new version"
    echo ""
    log_info "If you encounter issues:"
    echo "  - Run ./rollback.sh $backup_dir to restore this backup"
    echo "  - See the rollback procedure in UPGRADE-GUIDE.md"
    echo ""

    if [ "$start_status" -eq 1 ]; then
        exit 1
    fi
}

# Parse command-line arguments
DRY_RUN=false
TARGET_VERSION_ARG=""
for arg in "$@"; do
    case "$arg" in
        --dry-run)
            DRY_RUN=true
            ;;
        --help|-h)
            print_usage
            exit 0
            ;;
        *)
            TARGET_VERSION_ARG="$arg"
            ;;
    esac
done

# Run main function
main
