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

# Function to check if server is running
is_server_running() {
    docker ps --format '{{.Names}}' | grep -q "^private-mc-server$"
}

# Function to get current version from .env
get_current_version() {
    if [ -f .env ]; then
        grep "^MINECRAFT_VERSION=" .env | cut -d'=' -f2
    else
        echo "unknown"
    fi
}

# Function to create backup
create_backup() {
    local backup_dir="./backups/backup-$(date +%Y%m%d-%H%M%S)"
    
    log_info "Creating backup at: $backup_dir"
    mkdir -p "$backup_dir"
    
    # Use docker run to create a tarball backup from the volume
    docker run --rm \
        -v mcserver:/mcserver:ro \
        -v "$(pwd)/$backup_dir":/backup \
        ubuntu \
        tar czf /backup/mcserver-backup.tar.gz -C /mcserver . 2>/dev/null || {
            log_error "Backup failed!"
            return 1
        }
    
    # Verify backup was created
    if [ -f "$backup_dir/mcserver-backup.tar.gz" ]; then
        local backup_size=$(du -h "$backup_dir/mcserver-backup.tar.gz" | cut -f1)
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
    read -p "Enter the new Minecraft version (e.g., 1.21.9): " new_version
    
    if [ -z "$new_version" ]; then
        log_error "No version specified. Aborting."
        exit 1
    fi
    
    # Confirm upgrade
    echo ""
    log_warning "This will upgrade your server from $current_version to $new_version"
    read -p "Do you want to continue? (yes/no): " confirm
    
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
    if [ $? -ne 0 ]; then
        log_error "Backup failed! Aborting upgrade."
        exit 1
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
    
    if [ ${PIPESTATUS[0]} -eq 0 ]; then
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
    
    # Step 6: Monitor startup
    log_info "Step 6/6: Monitoring server startup..."
    log_info "Waiting for server to initialize..."
    sleep 5
    
    # Show recent logs
    echo ""
    log_info "Recent server logs:"
    echo "----------------------------------------"
    docker logs private-mc-server --tail 20 2>&1 || log_warning "Could not retrieve logs"
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
    log_info "Next steps:"
    echo "  1. Monitor logs: docker logs -f private-mc-server"
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
