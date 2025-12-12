#!/bin/bash
set -euo pipefail

# Minecraft Server Upgrade Script
# This script automates the upgrade process for the Minecraft server
# including backup management and version updates.

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
    log_info "Current Minecraft version: $current_version"
    echo ""
    
    # Prompt for new version
    read -r -p "Enter the new Minecraft version (e.g., 1.21.10): " new_version
    
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
    
    # Check if a recent backup exists (within last 60 minutes)
    recent_backup=$(find ./backups -maxdepth 1 -type d -name "backup-*" -mmin -60 2>/dev/null | head -1)
    
    if [ -n "$recent_backup" ] && [ -f "$recent_backup/mcserver-backup.tar.gz" ]; then
        log_info "Using recent backup: $recent_backup"
        backup_dir="$recent_backup"
    else
        log_info "No recent backup found, checking for any existing backup..."
        backup_dir=$(ls -td ./backups/backup-* 2>/dev/null | head -1)
        
        if [ -z "$backup_dir" ] || [ ! -f "$backup_dir/mcserver-backup.tar.gz" ]; then
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
            backup_dir=$(ls -td ./backups/backup-* 2>/dev/null | head -1)
            
            if [ -z "$backup_dir" ] || [ ! -f "$backup_dir/mcserver-backup.tar.gz" ]; then
                log_error "Backup creation succeeded but backup file not found!"
                exit 1
            fi
            
            log_success "Backup created successfully: $backup_dir"
        else
            log_info "Using existing backup: $backup_dir"
        fi
    fi
    
    log_success "Backup verified: $backup_dir"
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
    log_info "Waiting for server to initialize..."
    sleep 5
    
    local container_name
    container_name=$(get_env_value "CONTAINER_NAME" "open-mc-server")
    
    # Show recent logs
    echo ""
    log_info "Recent server logs:"
    echo "----------------------------------------"
    docker logs "$container_name" --tail 20 2>&1 || log_warning "Could not retrieve logs"
    echo "----------------------------------------"
    echo ""
    
    # Final summary
    echo "=========================================="
    log_success "Upgrade completed successfully!"
    send_alert "Server Upgrade Complete" "Successfully upgraded from $current_version to $new_version. Backup: $backup_dir" "INFO" "ALERTS_UPGRADE_COMPLETE"
    echo "=========================================="
    echo ""
    log_info "Summary:"
    echo "  - Previous version: $current_version"
    echo "  - New version: $new_version"
    echo "  - Backup location: $backup_dir"
    echo ""
    local container_name
    container_name=$(get_env_value "CONTAINER_NAME" "open-mc-server")
    log_info "Next steps:"
    echo "  1. Monitor logs: docker logs -f $container_name"
    echo "  2. Connect to the server and verify everything works"
    echo "  3. Check that plugins are compatible with the new version"
    echo ""
    log_info "If you encounter issues:"
    echo "  - See the rollback procedure in UPGRADE-GUIDE.md"
    echo "  - Your backup is available at: $backup_dir"
    echo ""
}

# Run main function
main
