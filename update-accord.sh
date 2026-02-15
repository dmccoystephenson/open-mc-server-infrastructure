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
    echo "  -y, --yes              Skip all prompts and use default behaviors (non-interactive mode)"
    echo "  -h, --help             Display this help message"
    echo ""
    echo "Examples:"
    echo "  $0                     # Update from main branch"
    echo "  $0 -b feature-chat     # Update from feature-chat branch"
    echo "  $0 --yes               # Non-interactive update with auto-confirmation"
    echo ""
}

# Parse command line arguments
BRANCH="main"
NON_INTERACTIVE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -b|--branch)
            BRANCH="$2"
            shift 2
            ;;
        -y|--yes)
            NON_INTERACTIVE=true
            shift
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

# Function to ensure accord-chat .env exists
ensure_accord_env() {
    local accord_env="accord-chat/.env"
    local accord_sample_env="accord-chat/sample.env"
    
    if [ ! -f "$accord_env" ]; then
        if [ -f "$accord_sample_env" ]; then
            log_info "Creating accord-chat/.env from sample.env..."
            cp "$accord_sample_env" "$accord_env"
            log_success "Created accord-chat/.env"
            log_info "Edit accord-chat/.env to customize Accord configuration"
        else
            log_warning "accord-chat/sample.env not found"
            log_warning "This may cause issues when starting Accord services"
        fi
    else
        log_info "accord-chat/.env already exists"
    fi
}

# Function to pull updates from Git
pull_updates() {
    log_info "Pulling updates from branch: $BRANCH"
    
    # Use pushd/popd for safer directory navigation
    pushd accord-chat > /dev/null
    
    # Check for uncommitted changes
    if ! git diff --quiet || ! git diff --cached --quiet; then
        log_warning "Uncommitted changes detected in accord-chat submodule"
        log_error "Please commit or stash your changes before updating"
        popd > /dev/null
        exit 1
    fi
    
    # Fetch all branches
    git fetch origin
    
    # Check if branch exists
    if ! git rev-parse --verify "origin/$BRANCH" >/dev/null 2>&1; then
        log_error "Branch '$BRANCH' does not exist in remote repository"
        popd > /dev/null
        exit 1
    fi
    
    # Get current commit
    local current_commit
    current_commit=$(git rev-parse HEAD)
    
    # Checkout and pull the specified branch
    git checkout "$BRANCH"
    git pull origin "$BRANCH"
    
    # Get new commit
    local new_commit
    new_commit=$(git rev-parse HEAD)
    
    popd > /dev/null
    
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
    
    # Rebuild and start Accord services in one step
    log_info "Rebuilding and starting accord-backend and accord-webapp..."
    docker compose up -d --build accord-backend accord-webapp
    
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
    if [ "$NON_INTERACTIVE" = true ]; then
        log_info "Running in non-interactive mode"
    fi
    echo ""
    
    # Check submodule initialization
    check_submodule
    echo ""
    
    # Ensure accord-chat/.env exists
    ensure_accord_env
    echo ""
    
    # Pull updates and track if there were updates
    local has_updates=false
    if pull_updates; then
        has_updates=true
        echo ""
    else
        echo ""
        log_info "No code updates available"
    fi
    
    # If there are updates, restart services
    if [ "$has_updates" = true ]; then
        log_warning "Code updates detected. Services need to be restarted."
        
        if [ "$NON_INTERACTIVE" = true ]; then
            confirm="yes"
            log_info "Non-interactive mode: automatically restarting services"
        else
            read -r -p "Would you like to restart Accord services now? (yes/no): " confirm
        fi
        
        if [ "$confirm" = "yes" ] || [ "$confirm" = "y" ]; then
            echo ""
            restart_accord_services
            echo ""
            show_status
        else
            log_info "Skipping service restart"
            log_warning "Remember to restart services manually with:"
            echo "  docker compose up -d --build accord-backend accord-webapp"
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
    echo "  - Rebuild and restart: docker compose up -d --build accord-backend accord-webapp"
    echo "  - Stop services: docker compose stop accord-backend accord-webapp"
    echo "  - Start services: docker compose up -d accord-backend accord-webapp"
    echo ""
}

# Run main function
main
