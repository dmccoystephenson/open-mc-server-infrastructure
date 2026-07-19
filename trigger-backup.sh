#!/bin/bash
set -euo pipefail

# Trigger Backup Script
# This script triggers a manual backup via the backup-manager REST API

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

# Main function
main() {
    echo "=========================================="
    echo "  Trigger Manual Backup"
    echo "=========================================="
    echo ""
    
    # Get backup port from .env or use default
    local backup_port
    backup_port=$(get_env_value "BACKUP_PORT" "8091")

    # Validate the port is numeric before building a URL from it
    if ! [[ "$backup_port" =~ ^[0-9]+$ ]]; then
        log_error "BACKUP_PORT '$backup_port' is not a valid port number. Check .env."
        exit 1
    fi

    # Determine the backup manager URL
    local backup_url="http://localhost:${backup_port}/api/backups/trigger"

    log_info "Triggering backup via API..."
    log_info "URL: $backup_url"
    echo ""

    # Check if curl is available
    if ! command -v curl >/dev/null 2>&1; then
        log_error "curl is not installed. Please install curl to use this script."
        exit 1
    fi

    # Preflight: confirm backup-manager is actually reachable before triggering,
    # so an unreachable service produces an actionable hint instead of a bare
    # "Connection refused" from the trigger request below.
    if ! curl -sf --connect-timeout 3 --max-time 5 "http://localhost:${backup_port}/actuator/health" >/dev/null 2>&1; then
        log_error "backup-manager is not reachable at localhost:${backup_port}."
        log_info "Check that it is running: docker compose ps open-mc-backup-manager"
        exit 1
    fi

    # Trigger the backup
    local response
    local http_code
    
    response=$(curl -X POST "$backup_url" \
        -H "Content-Type: application/json" \
        -w "\n%{http_code}" \
        --max-time 300 \
        --connect-timeout 10 \
        -s 2>&1)
    
    http_code=$(echo "$response" | tail -1)
    body=$(echo "$response" | sed '$d')
    
    echo ""
    
    if [ "$http_code" = "200" ]; then
        log_success "Backup triggered successfully!"
        echo ""
        log_info "Response:"
        echo "$body" | grep -o '"backupPath":"[^"]*"' | sed 's/"backupPath":"/  Backup location: /' | sed 's/"$//'
        echo ""
    else
        log_error "Backup trigger failed with HTTP code: $http_code"
        echo ""
        if [ -n "$body" ]; then
            log_info "Response:"
            echo "$body"
        fi
        echo ""
        exit 1
    fi
    
    echo "=========================================="
    log_success "Done!"
    echo "=========================================="
}

# Run main function
main
