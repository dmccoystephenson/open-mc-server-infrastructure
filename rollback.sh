#!/bin/bash
set -euo pipefail

# Minecraft Server Rollback Script
# Restores the server's data volume from a backup created by backup-manager
# (see UPGRADE-GUIDE.md) and restarts the server. Intended as the automated
# counterpart to ./upgrade.sh when an upgrade needs to be undone.
#
# Usage:
#   ./rollback.sh                       List available backups and exit.
#   ./rollback.sh 20260115-020000       Restore backup-20260115-020000.
#   ./rollback.sh backup-20260115-020000   Same as above (prefix optional).

cd "$(dirname "$0")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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
    local source="rollback-script"

    local alert_url
    if [ -n "${ALERT_MANAGER_URL:-}" ]; then
        alert_url="$ALERT_MANAGER_URL"
    elif [ -f /.dockerenv ] || grep -q docker /proc/1/cgroup 2>/dev/null; then
        alert_url="http://alert-manager:8090/api/alerts"
    else
        alert_url="http://localhost:8090/api/alerts"
    fi

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

# Human-readable disk usage for a path inside a named volume.
volume_disk_usage() {
    local volume=$1
    local subpath="${2:-}"
    local out
    out=$(docker run --rm -v "${volume}:/vol:ro" alpine sh -c \
        'du -sh "/vol$1" 2>/dev/null' _ "$subpath") || { echo "unknown"; return; }
    echo "$out" | awk '{print $1}'
}

print_usage() {
    cat <<'EOF'
Usage: ./rollback.sh [backup-name|timestamp]

Restores the Minecraft server's data volume from a previous backup created
by backup-manager, then restarts the server.

  ./rollback.sh                          List available backups and exit.
  ./rollback.sh 20260115-020000          Restore the backup-20260115-020000 backup.
  ./rollback.sh backup-20260115-020000   Same as above (backup- prefix optional).

WARNING: this PERMANENTLY REPLACES the current contents of the server's
data volume with the chosen backup. The server is stopped before restoring
and started again afterward.
EOF
}

list_available_backups() {
    local backups_volume=$1

    echo "=========================================="
    echo "  Available backups (newest first)"
    echo "=========================================="

    local found=0
    while IFS= read -r name; do
        [ -z "$name" ] && continue
        found=1
        local size status
        size=$(volume_disk_usage "$backups_volume" "/$name")
        if backup_is_complete "$backups_volume" "$name"; then
            status="complete"
        else
            status="incomplete"
        fi
        printf '  %-28s %-10s %s\n' "$name" "$size" "$status"
    done < <(list_backup_dirs "$backups_volume")

    if [ "$found" -eq 0 ]; then
        log_warning "No backups found in volume '$backups_volume'."
    fi
    echo ""
}

main() {
    local requested="${1:-}"

    case "$requested" in
        --help|-h)
            print_usage
            exit 0
            ;;
    esac

    local backups_volume mcserver_volume
    backups_volume=$(get_env_value "BACKUPS_VOLUME_NAME" "backups")
    mcserver_volume=$(get_env_value "VOLUME_NAME" "mcserver")

    if [ -z "$requested" ]; then
        list_available_backups "$backups_volume"
        print_usage
        exit 0
    fi

    # Normalize to "backup-<timestamp>" and validate the timestamp shape
    # strictly — this value is later interpolated into a path inside a
    # container, so it must never contain path separators or shell
    # metacharacters.
    local backup_name
    case "$requested" in
        backup-*) backup_name="$requested" ;;
        *) backup_name="backup-$requested" ;;
    esac

    if ! echo "$backup_name" | grep -qE '^backup-[0-9]{8}-[0-9]{6}$'; then
        log_error "Invalid backup name/timestamp: '$requested'"
        log_info "Expected format: YYYYMMDD-HHMMSS (e.g. 20260115-020000)"
        exit 1
    fi

    if ! backup_is_complete "$backups_volume" "$backup_name"; then
        log_error "Backup '$backup_name' not found or incomplete in volume '$backups_volume'."
        echo ""
        list_available_backups "$backups_volume"
        exit 1
    fi

    echo ""
    log_warning "This will PERMANENTLY REPLACE the current contents of volume '$mcserver_volume'"
    log_warning "with the contents of '$backup_name'. This cannot be undone."
    read -r -p "Type 'yes' to continue: " confirm
    if [ "$confirm" != "yes" ]; then
        log_info "Rollback cancelled."
        exit 0
    fi
    echo ""

    log_info "Stopping the server..."
    ./down.sh
    echo ""

    log_info "Restoring '$backup_name' into volume '$mcserver_volume'..."
    docker run --rm \
        --user 1000:1000 \
        -v "${backups_volume}:/backups:ro" \
        -v "${mcserver_volume}:/mcserver" \
        alpine sh -c '
            cd /mcserver
            rm -rf -- ..?* .[!.]* * 2>/dev/null || true
            tar xzf "/backups/$1/mcserver-backup.tar.gz" -C /mcserver
        ' _ "$backup_name"
    log_success "Restore complete."
    echo ""

    log_info "Starting the server..."
    ./up.sh
    echo ""

    local container_name
    container_name=$(get_env_value "CONTAINER_NAME" "open-mc-server")

    send_alert "Server Rollback Complete" "Restored from backup '$backup_name'." "INFO"

    echo "=========================================="
    log_success "Rollback to '$backup_name' completed."
    echo "=========================================="
    echo ""
    log_info "Next steps:"
    echo "  1. Monitor logs: docker logs -f $container_name"
    echo "  2. Connect to the server and verify everything works"
    echo ""
}

main "$@"
