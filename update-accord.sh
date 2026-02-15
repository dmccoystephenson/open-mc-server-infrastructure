#!/bin/bash
set -euo pipefail

# Accord Update Script
# This script automates the process of updating the Accord chat application
# including pulling new code, detecting environment changes, and restarting services.

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

# Function to display usage
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -b, --branch BRANCH    Git branch to pull from (default: main)"
    echo "  -h, --help             Display this help message"
    echo ""
    echo "Examples:"
    echo "  $0                     # Update from main branch"
    echo "  $0 -b feature-chat     # Update from feature-chat branch"
    echo ""
}

# Parse command line arguments
BRANCH="main"

while [[ $# -gt 0 ]]; do
    case $1 in
        -b|--branch)
            BRANCH="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Function to check if accord-chat submodule is initialized
check_submodule() {
    if [ ! -d "accord-chat/.git" ]; then
        log_error "Accord chat submodule is not initialized!"
        log_info "Initializing submodule..."
        git submodule update --init --recursive
        log_success "Submodule initialized"
    fi
}

# Function to get environment variables from a .env file
get_env_vars() {
    local env_file="$1"
    if [ ! -f "$env_file" ]; then
        echo ""
        return
    fi
    
    # Extract variable names (lines that start with a word character and contain =)
    # Exclude comments and empty lines
    grep -E '^[A-Z_][A-Z0-9_]*=' "$env_file" | cut -d'=' -f1 | sort -u
}

# Function to detect new environment variables
detect_new_env_vars() {
    local accord_sample_env="accord-chat/sample.env"
    local root_env=".env"
    local root_sample_env="sample.env"
    
    log_info "Checking for new environment variables..."
    
    if [ ! -f "$accord_sample_env" ]; then
        log_warning "Accord sample.env not found, skipping environment variable detection"
        return
    fi
    
    # Get environment variables from accord-chat/sample.env
    local accord_vars=$(get_env_vars "$accord_sample_env")
    
    if [ -z "$accord_vars" ]; then
        log_info "No environment variables found in Accord sample.env"
        return
    fi
    
    # Determine which file to compare against
    local compare_file=""
    if [ -f "$root_env" ]; then
        compare_file="$root_env"
    elif [ -f "$root_sample_env" ]; then
        compare_file="$root_sample_env"
        log_warning "Root .env not found, comparing with sample.env"
    else
        log_warning "Neither .env nor sample.env found in root directory"
        log_info "Cannot detect new variables without a reference file"
        return
    fi
    
    local existing_vars=$(get_env_vars "$compare_file")
    
    # Find variables that are in Accord but not in root
    local new_vars=""
    while IFS= read -r var; do
        if ! echo "$existing_vars" | grep -q "^${var}$"; then
            new_vars="${new_vars}${var}\n"
        fi
    done <<< "$accord_vars"
    
    if [ -z "$new_vars" ]; then
        log_success "No new environment variables detected"
        return
    fi
    
    log_warning "New environment variables detected:"
    echo -e "$new_vars" | grep -v '^$' | sed 's/^/  - /'
    echo ""
    
    # If .env doesn't exist but sample.env does, suggest creating it
    if [ ! -f "$root_env" ] && [ -f "$root_sample_env" ]; then
        log_info "You should create .env from sample.env first:"
        log_info "  cp sample.env .env"
        return
    fi
    
    if [ ! -f "$root_env" ]; then
        log_warning "Cannot append new variables: .env file not found"
        return
    fi
    
    # Ask user if they want to append new variables
    read -r -p "Would you like to append these variables to .env? (yes/no): " confirm
    
    if [ "$confirm" != "yes" ] && [ "$confirm" != "y" ]; then
        log_info "Skipping environment variable update"
        return
    fi
    
    # Append new variables to .env
    echo "" >> "$root_env"
    echo "# Accord Chat Variables (added by update-accord.sh on $(date))" >> "$root_env"
    
    while IFS= read -r var; do
        if [ -n "$var" ]; then
            # Get the value from accord sample.env
            local value=$(grep "^${var}=" "$accord_sample_env" | head -1 | cut -d'=' -f2-)
            echo "${var}=${value}" >> "$root_env"
            log_success "Added ${var} to .env"
        fi
    done <<< "$(echo -e "$new_vars" | grep -v '^$')"
    
    log_success "Environment variables updated in .env"
}

# Function to pull updates from Git
pull_updates() {
    log_info "Pulling updates from branch: $BRANCH"
    
    cd accord-chat
    
    # Fetch all branches
    git fetch origin
    
    # Check if branch exists
    if ! git rev-parse --verify "origin/$BRANCH" >/dev/null 2>&1; then
        log_error "Branch '$BRANCH' does not exist in remote repository"
        cd ..
        exit 1
    fi
    
    # Get current commit
    local current_commit=$(git rev-parse HEAD)
    
    # Checkout and pull the specified branch
    git checkout "$BRANCH"
    git pull origin "$BRANCH"
    
    # Get new commit
    local new_commit=$(git rev-parse HEAD)
    
    cd ..
    
    # Check if there were any updates
    if [ "$current_commit" = "$new_commit" ]; then
        log_info "Already up to date (commit: ${new_commit:0:8})"
        return 1
    else
        log_success "Updated from ${current_commit:0:8} to ${new_commit:0:8}"
        return 0
    fi
}

# Function to restart Accord services
restart_accord_services() {
    log_info "Restarting Accord services..."
    
    # Stop Accord services
    log_info "Stopping accord-backend and accord-webapp..."
    docker compose stop accord-backend accord-webapp
    
    # Rebuild the services
    log_info "Rebuilding Accord services..."
    docker compose build accord-backend accord-webapp
    
    # Start the services
    log_info "Starting accord-backend and accord-webapp..."
    docker compose up -d accord-backend accord-webapp
    
    log_success "Accord services restarted successfully"
}

# Function to show service status
show_status() {
    log_info "Checking Accord service status..."
    echo ""
    docker compose ps accord-backend accord-webapp
    echo ""
}

# Main update process
main() {
    echo "=========================================="
    echo "  Accord Chat Update Script"
    echo "=========================================="
    echo ""
    
    log_info "Target branch: $BRANCH"
    echo ""
    
    # Check submodule initialization
    check_submodule
    echo ""
    
    # Pull updates
    if pull_updates; then
        local has_updates=true
        echo ""
    else
        local has_updates=false
        echo ""
        log_info "No code updates available"
    fi
    
    # Detect new environment variables
    detect_new_env_vars
    echo ""
    
    # If there are updates, restart services
    if [ "$has_updates" = true ]; then
        log_warning "Code updates detected. Services need to be restarted."
        read -r -p "Would you like to restart Accord services now? (yes/no): " confirm
        
        if [ "$confirm" = "yes" ] || [ "$confirm" = "y" ]; then
            echo ""
            restart_accord_services
            echo ""
            show_status
        else
            log_info "Skipping service restart"
            log_warning "Remember to restart services manually with:"
            echo "  docker compose restart accord-backend accord-webapp"
        fi
    else
        log_info "No service restart needed"
    fi
    
    echo ""
    echo "=========================================="
    log_success "Accord update process completed"
    echo "=========================================="
    echo ""
    
    log_info "Quick reference:"
    echo "  - View logs: docker compose logs -f accord-backend accord-webapp"
    echo "  - Restart manually: docker compose restart accord-backend accord-webapp"
    echo "  - Stop services: docker compose stop accord-backend accord-webapp"
    echo "  - Start services: docker compose up -d accord-backend accord-webapp"
    echo ""
}

# Run main function
main
