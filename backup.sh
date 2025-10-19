#!/bin/bash
set -euo pipefail

# Minecraft Server Backup Script
# This script creates a backup of the Minecraft server files from the Docker volume.

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

# Function to create backup
create_backup() {
    local backup_dir
    backup_dir="./backups/backup-$(date +%Y%m%d-%H%M%S)"
    
    local volume_name
    volume_name=$(get_env_value "VOLUME_NAME" "mcserver")
    
    log_info "Creating backup at: $backup_dir" >&2
    mkdir -p "$backup_dir"
    
    # Use docker run to create a tarball backup from the volume
    docker run --rm \
        -v "${volume_name}:/mcserver:ro" \
        -v "$(pwd)/$backup_dir":/backup \
        ubuntu \
        tar czf /backup/mcserver-backup.tar.gz -C /mcserver . 2>/dev/null || {
            log_error "Backup failed!" >&2
            return 1
        }
    
    # Verify backup was created
    if [ -f "$backup_dir/mcserver-backup.tar.gz" ]; then
        local backup_size
        backup_size=$(du -h "$backup_dir/mcserver-backup.tar.gz" | cut -f1)
        log_success "Backup created successfully: $backup_dir/mcserver-backup.tar.gz ($backup_size)" >&2
        echo "$backup_dir"
        return 0
    else
        log_error "Backup verification failed!" >&2
        return 1
    fi
}

# Main backup process
main() {
    echo "=========================================="
    echo "  Minecraft Server Backup Script"
    echo "=========================================="
    echo ""
    
    # Check if .env exists
    if [ ! -f .env ]; then
        log_warning ".env file not found! Using default volume name 'mcserver'"
        log_info "Create .env from sample.env to customize settings."
    fi
    
    log_info "Starting backup process..."
    echo ""
    
    backup_dir=$(create_backup)
    backup_result=$?
    
    echo ""
    
    if [ "$backup_result" -eq 0 ]; then
        echo "=========================================="
        log_success "Backup completed successfully!"
        echo "=========================================="
        echo ""
        log_info "Backup location: $backup_dir"
        echo ""
        log_info "To restore from this backup:"
        echo "  1. Stop the server: ./down.sh"
        echo "  2. Extract backup to volume:"
        echo "     docker run --rm \\"
        echo "       -v mcserver:/mcserver \\"
        echo "       -v \"$(pwd)/$backup_dir\":/backup \\"
        echo "       ubuntu \\"
        echo "       tar xzf /backup/mcserver-backup.tar.gz -C /mcserver"
        echo "  3. Start the server: ./up.sh"
        echo ""
    else
        log_error "Backup failed! Please check the error messages above."
        exit 1
    fi
}

# Run main function
main
