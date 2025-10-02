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
    local container_name=$(get_env_value "CONTAINER_NAME" "private-mc-server")
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

# Function to get volume disk usage
get_volume_usage() {
    local volume_name=$(get_env_value "VOLUME_NAME" "mcserver")
    
    # Check if volume exists
    if ! docker volume inspect "$volume_name" &>/dev/null; then
        echo "N/A (volume does not exist)"
        return
    fi
    
    # Get disk usage of the volume
    local usage=$(docker run --rm -v "${volume_name}:/mcserver:ro" ubuntu du -sh /mcserver 2>/dev/null | cut -f1 || echo "N/A")
    echo "$usage"
}

# Function to perform dry-run
dry_run() {
    local new_version=$1
    local current_version=$(get_current_version)
    local backup_dir="./backups/backup-$(date +%Y%m%d-%H%M%S)"
    local volume_usage=$(get_volume_usage)
    
    echo "=========================================="
    echo "  Upgrade Dry Run"
    echo "=========================================="
    echo ""
    log_info "Upgrade Plan:"
    echo ""
    echo "  Current Version:        $current_version"
    echo "  Target Version:         $new_version"
    echo "  Current Disk Usage:     $volume_usage"
    echo "  Planned Backup Location: $backup_dir"
    echo ""
    log_info "Steps that will be performed:"
    echo "  1. Stop the server"
    echo "  2. Create backup at $backup_dir"
    echo "  3. Update MINECRAFT_VERSION in .env to $new_version"
    echo "  4. Rebuild Docker image (10-15 minutes)"
    echo "  5. Start the server"
    echo "  6. Verify server startup"
    echo ""
    log_info "To proceed with the upgrade, run:"
    echo "  ./upgrade.sh"
    echo ""
}

# Function to create backup
create_backup() {
    local backup_dir
    backup_dir="./backups/backup-$(date +%Y%m%d-%H%M%S)"
    
    local volume_name=$(get_env_value "VOLUME_NAME" "mcserver")
    
    log_info "Creating backup at: $backup_dir"
    mkdir -p "$backup_dir"
    
    # Use docker run to create a tarball backup from the volume
    docker run --rm \
        -v "${volume_name}:/mcserver:ro" \
        -v "$(pwd)/$backup_dir":/backup \
        ubuntu \
        tar czf /backup/mcserver-backup.tar.gz -C /mcserver . 2>/dev/null || {
            log_error "Backup failed!"
            return 1
        }
    
    # Verify backup was created
    if [ -f "$backup_dir/mcserver-backup.tar.gz" ]; then
        local backup_size
        backup_size=$(du -h "$backup_dir/mcserver-backup.tar.gz" | cut -f1)
        log_success "Backup created successfully: $backup_dir/mcserver-backup.tar.gz ($backup_size)"
        echo "$backup_dir"
        return 0
    else
        log_error "Backup verification failed!"
        return 1
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

# Function to verify server startup
verify_server_startup() {
    local container_name=$(get_env_value "CONTAINER_NAME" "private-mc-server")
    local max_wait=120  # Wait up to 2 minutes
    local wait_time=0
    local check_interval=5
    
    log_info "Verifying server startup..."
    
    while [ $wait_time -lt $max_wait ]; do
        # Check if container is still running
        if ! docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
            log_error "Container stopped unexpectedly!"
            return 1
        fi
        
        # Check logs for successful startup indicator
        if docker logs "$container_name" 2>&1 | grep -q "Done ([0-9]*\.[0-9]*s)! For help, type \"help\""; then
            log_success "Server started successfully!"
            return 0
        fi
        
        # Check for critical errors
        if docker logs "$container_name" 2>&1 | grep -qE "(Error|Exception|Failed to start|Could not load)" | grep -v "warnings"; then
            log_warning "Potential errors detected in logs. Please review."
        fi
        
        sleep $check_interval
        wait_time=$((wait_time + check_interval))
        log_info "Waiting for server startup... ($wait_time/${max_wait}s)"
    done
    
    log_warning "Server startup verification timed out after ${max_wait}s"
    log_info "Please check logs manually: docker logs -f $container_name"
    return 1
}

# Main upgrade process
main() {
    # Parse command line arguments
    local dry_run_mode=false
    local new_version=""
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --dry-run)
                dry_run_mode=true
                shift
                ;;
            --help|-h)
                echo "Usage: $0 [OPTIONS] [VERSION]"
                echo ""
                echo "Options:"
                echo "  --dry-run [VERSION]  Display upgrade plan without executing"
                echo "  --help, -h           Show this help message"
                echo ""
                echo "Examples:"
                echo "  $0                   # Interactive upgrade"
                echo "  $0 1.21.10           # Upgrade to version 1.21.10"
                echo "  $0 --dry-run 1.21.10 # Preview upgrade to 1.21.10"
                echo ""
                exit 0
                ;;
            *)
                new_version="$1"
                shift
                ;;
        esac
    done
    
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
    
    # Prompt for new version if not provided
    if [ -z "$new_version" ]; then
        read -r -p "Enter the new Minecraft version (e.g., 1.21.9): " new_version
    fi
    
    if [ -z "$new_version" ]; then
        log_error "No version specified. Aborting."
        exit 1
    fi
    
    # If dry-run mode, show plan and exit
    if [ "$dry_run_mode" = true ]; then
        dry_run "$new_version"
        exit 0
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
    echo ""
    
    # Step 1: Stop the server
    log_info "Step 1/6: Stopping the server..."
    if is_server_running; then
        ./down.sh
        log_success "Server stopped successfully"
    else
        log_info "Server is not running, skipping stop step"
    fi
    echo ""
    
    # Step 2: Create backup
    log_info "Step 2/6: Creating backup..."
    backup_dir=$(create_backup)
    backup_result=$?
    if [ "$backup_result" -ne 0 ]; then
        log_error "Backup failed! Aborting upgrade."
        exit 1
    fi
    
    # Save the .env file to the backup directory for rollback purposes
    cp .env "$backup_dir/.env.backup" 2>/dev/null || true
    
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
        exit 1
    fi
    echo ""
    
    # Step 5: Start the server
    log_info "Step 5/6: Starting the server..."
    ./up.sh
    log_success "Server started"
    echo ""
    
    # Step 6: Monitor startup and verify
    log_info "Step 6/6: Verifying server startup..."
    if verify_server_startup; then
        echo ""
    else
        log_error "Server startup verification failed!"
        log_warning "Please check the logs manually: docker logs -f $(get_env_value 'CONTAINER_NAME' 'private-mc-server')"
        log_warning "Your backup is available at: $backup_dir"
        log_info "To rollback, use: ./rollback.sh"
        exit 1
    fi
    
    local container_name=$(get_env_value "CONTAINER_NAME" "private-mc-server")
    
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
    echo "=========================================="
    echo ""
    log_info "Summary:"
    echo "  - Previous version: $current_version"
    echo "  - New version: $new_version"
    echo "  - Backup location: $backup_dir"
    echo ""
    local container_name=$(get_env_value "CONTAINER_NAME" "private-mc-server")
    log_info "Next steps:"
    echo "  1. Monitor logs: docker logs -f $container_name"
    echo "  2. Connect to the server and verify everything works"
    echo "  3. Check that plugins are compatible with the new version"
    echo ""
    log_info "If you encounter issues:"
    echo "  - Run: ./rollback.sh"
    echo "  - Your backup is available at: $backup_dir"
    echo ""
}

# Run main function with all arguments
main "$@"
