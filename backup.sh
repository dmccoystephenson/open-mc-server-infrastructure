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
    # Use BACKUP_DIRECTORY env var if set, otherwise default to ./backups
    local base_backup_dir
    base_backup_dir="${BACKUP_DIRECTORY:-./backups}"
    
    local backup_dir
    backup_dir="${base_backup_dir}/backup-$(date +%Y%m%d-%H%M%S)"
    
    local volume_name
    volume_name=$(get_env_value "VOLUME_NAME" "mcserver")
    
    log_info "Creating backup at: $backup_dir" >&2
    mkdir -p "$backup_dir"
    
    # Check if volume exists
    log_info "Checking if volume '$volume_name' exists..." >&2
    if ! docker volume inspect "$volume_name" >/dev/null 2>&1; then
        log_error "Volume '$volume_name' does not exist!" >&2
        log_error "Please ensure the server has been started at least once." >&2
        return 1
    fi
    log_success "Volume '$volume_name' found." >&2
    
    # Check if ubuntu image is available, pull if needed
    log_info "Checking for ubuntu Docker image..." >&2
    if ! docker image inspect ubuntu:latest >/dev/null 2>&1; then
        log_info "Ubuntu image not found locally. Pulling from Docker Hub..." >&2
        log_info "This may take a few minutes on first run..." >&2
        if ! docker pull ubuntu:latest >&2; then
            log_error "Failed to pull ubuntu image!" >&2
            return 1
        fi
        log_success "Ubuntu image pulled successfully." >&2
    else
        log_success "Ubuntu image found." >&2
    fi
    
    # Use docker run to create a tarball backup from the volume
    log_info "Creating compressed backup archive (this may take a while)..." >&2
    
    # Run docker command and capture output to a temp file
    local temp_output
    temp_output=$(mktemp -t backup.XXXXXX)
    trap 'rm -f "$temp_output"' RETURN
    local docker_exit_code
    
    # Extract just the backup subdirectory name (e.g., backup-20251029-020439)
    local backup_subdir
    backup_subdir=$(basename "$backup_dir")
    
    # Determine which path to use for docker run mount
    # If HOST_BACKUP_DIRECTORY is set, use it (when running from container with docker socket)
    # Otherwise use the local base_backup_dir (when running standalone)
    local docker_mount_path
    if [ -n "${HOST_BACKUP_DIRECTORY:-}" ]; then
        docker_mount_path="${HOST_BACKUP_DIRECTORY}"
    else
        docker_mount_path="${base_backup_dir}"
    fi
    
    # Mount the base backup directory and create the tar file in the subdirectory
    docker run --rm \
        -v "${volume_name}:/mcserver:ro" \
        -v "${docker_mount_path}:/backups" \
        ubuntu:latest \
        tar czf "/backups/${backup_subdir}/mcserver-backup.tar.gz" -C /mcserver . 2>&1 | tee "$temp_output" >&2
    docker_exit_code=${PIPESTATUS[0]}
    
    # Log any tar warnings/errors from the output
    if [ -s "$temp_output" ]; then
        while IFS= read -r line; do
            if [[ "$line" =~ (Error|error|Warning|warning|Cannot|cannot|Failed|failed) ]]; then
                log_warning "tar: $line" >&2
            fi
        done < "$temp_output"
    fi
    
    # Check if docker/tar command succeeded
    # Exit code 1 from tar means "some files changed during backup" which is acceptable for live systems
    # Exit code 2 or higher indicates a fatal error
    if [ "$docker_exit_code" -eq 0 ]; then
        log_success "Backup archive created successfully." >&2
    elif [ "$docker_exit_code" -eq 1 ]; then
        log_warning "Backup completed with warnings (files changed during backup)." >&2
        log_info "This is normal for a running server and the backup should still be usable." >&2
    else
        log_error "Backup failed! Exit code: $docker_exit_code" >&2
        return 1
    fi
    
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
        echo "       ubuntu:latest \\"
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
