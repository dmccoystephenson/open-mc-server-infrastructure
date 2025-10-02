#!/bin/bash
set -euo pipefail

# Minecraft Server Rollback Script
# This script automates the rollback process to restore from a backup

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

# Function to list available backups
list_backups() {
    if [ ! -d "./backups" ]; then
        log_error "No backups directory found!"
        return 1
    fi
    
    local backups=(./backups/backup-*)
    if [ ! -e "${backups[0]}" ]; then
        log_error "No backups found in ./backups/"
        return 1
    fi
    
    echo ""
    log_info "Available backups:"
    echo ""
    
    local index=1
    for backup in "${backups[@]}"; do
        if [ -d "$backup" ]; then
            local backup_name=$(basename "$backup")
            local backup_date=$(echo "$backup_name" | sed 's/backup-//' | sed 's/\([0-9]\{8\}\)-\([0-9]\{6\}\)/\1 \2/')
            local backup_size="N/A"
            if [ -f "$backup/mcserver-backup.tar.gz" ]; then
                backup_size=$(du -h "$backup/mcserver-backup.tar.gz" | cut -f1)
            fi
            
            # Check if there's a saved .env file
            local version="unknown"
            if [ -f "$backup/.env.backup" ]; then
                version=$(grep "^MINECRAFT_VERSION=" "$backup/.env.backup" | cut -d'=' -f2 || echo "unknown")
            fi
            
            echo "  [$index] $backup_name"
            echo "      Date: $backup_date"
            echo "      Size: $backup_size"
            echo "      Version: $version"
            echo ""
            index=$((index + 1))
        fi
    done
    
    return 0
}

# Function to restore from backup
restore_backup() {
    local backup_dir=$1
    local volume_name=$(get_env_value "VOLUME_NAME" "mcserver")
    
    if [ ! -d "$backup_dir" ]; then
        log_error "Backup directory not found: $backup_dir"
        return 1
    fi
    
    if [ ! -f "$backup_dir/mcserver-backup.tar.gz" ]; then
        log_error "Backup file not found: $backup_dir/mcserver-backup.tar.gz"
        return 1
    fi
    
    log_info "Restoring from backup: $backup_dir"
    
    # Restore the data
    docker run --rm \
        -v "${volume_name}:/mcserver" \
        -v "$(pwd)/$backup_dir":/backup \
        ubuntu \
        tar xzf /backup/mcserver-backup.tar.gz -C /mcserver || {
            log_error "Restore failed!"
            return 1
        }
    
    log_success "Data restored from backup"
    
    # Restore .env if available
    if [ -f "$backup_dir/.env.backup" ]; then
        log_info "Restoring .env configuration..."
        cp "$backup_dir/.env.backup" .env
        log_success ".env restored from backup"
    else
        log_warning "No .env backup found. You may need to manually update MINECRAFT_VERSION in .env"
    fi
    
    return 0
}

# Main rollback process
main() {
    echo "=========================================="
    echo "  Minecraft Server Rollback Script"
    echo "=========================================="
    echo ""
    
    log_warning "This script will restore your server from a backup."
    log_warning "Current server data will be replaced!"
    echo ""
    
    # List available backups
    if ! list_backups; then
        exit 1
    fi
    
    # Prompt for backup selection
    read -r -p "Enter the number of the backup to restore (or 'q' to quit): " selection
    
    if [ "$selection" = "q" ] || [ "$selection" = "Q" ]; then
        log_info "Rollback cancelled."
        exit 0
    fi
    
    # Validate selection
    if ! [[ "$selection" =~ ^[0-9]+$ ]]; then
        log_error "Invalid selection. Please enter a number."
        exit 1
    fi
    
    # Get the selected backup
    local backups=(./backups/backup-*)
    local backup_index=$((selection - 1))
    
    if [ $backup_index -lt 0 ] || [ $backup_index -ge ${#backups[@]} ]; then
        log_error "Invalid backup number."
        exit 1
    fi
    
    local selected_backup="${backups[$backup_index]}"
    
    if [ ! -d "$selected_backup" ]; then
        log_error "Selected backup does not exist: $selected_backup"
        exit 1
    fi
    
    # Confirm rollback
    echo ""
    log_warning "You are about to restore from: $(basename "$selected_backup")"
    read -r -p "Do you want to continue? (yes/no): " confirm
    
    if [ "$confirm" != "yes" ] && [ "$confirm" != "y" ]; then
        log_info "Rollback cancelled."
        exit 0
    fi
    
    echo ""
    log_info "Starting rollback process..."
    echo ""
    
    # Step 1: Stop the server
    log_info "Step 1/5: Stopping the server..."
    if is_server_running; then
        ./down.sh
        log_success "Server stopped successfully"
    else
        log_info "Server is not running"
    fi
    echo ""
    
    # Step 2: Remove current volume
    log_info "Step 2/5: Removing current server volume..."
    local volume_name=$(get_env_value "VOLUME_NAME" "mcserver")
    
    if docker volume inspect "$volume_name" &>/dev/null; then
        docker volume rm "$volume_name" || {
            log_error "Failed to remove volume. Make sure the server is stopped."
            exit 1
        }
        log_success "Volume removed"
    else
        log_info "Volume does not exist, skipping removal"
    fi
    
    # Recreate the volume
    docker volume create "$volume_name" &>/dev/null
    log_success "Volume recreated"
    echo ""
    
    # Step 3: Restore from backup
    log_info "Step 3/5: Restoring from backup..."
    if ! restore_backup "$selected_backup"; then
        log_error "Restore failed! Server may be in an inconsistent state."
        exit 1
    fi
    echo ""
    
    # Step 4: Rebuild Docker image
    log_info "Step 4/5: Rebuilding Docker image with restored version..."
    
    local restored_version=$(grep "^MINECRAFT_VERSION=" .env | cut -d'=' -f2 || echo "unknown")
    log_info "Restored version: $restored_version"
    
    log_warning "This may take 10-15 minutes as it compiles Spigot from source..."
    docker compose build --no-cache 2>&1 | while IFS= read -r line; do
        echo "  $line"
    done
    
    if [ "${PIPESTATUS[0]}" -eq 0 ]; then
        log_success "Docker image rebuilt successfully"
    else
        log_error "Docker build failed!"
        exit 1
    fi
    echo ""
    
    # Step 5: Start the server
    log_info "Step 5/5: Starting the server..."
    ./up.sh
    log_success "Server started"
    echo ""
    
    # Wait for server to initialize
    log_info "Waiting for server to initialize..."
    sleep 5
    
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
    log_success "Rollback completed successfully!"
    echo "=========================================="
    echo ""
    log_info "Summary:"
    echo "  - Restored from: $(basename "$selected_backup")"
    echo "  - Current version: $restored_version"
    echo ""
    log_info "Next steps:"
    echo "  1. Monitor logs: docker logs -f $container_name"
    echo "  2. Connect to the server and verify everything works"
    echo "  3. Check that your world data is intact"
    echo ""
}

# Run main function
main
