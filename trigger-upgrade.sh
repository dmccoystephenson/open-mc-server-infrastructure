#!/bin/bash
set -euo pipefail

# Trigger Upgrade Script
# This script triggers a server upgrade via the upgrade-manager REST API

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print colored messages
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if version argument provided
if [ $# -eq 0 ]; then
    log_error "No version specified"
    echo "Usage: $0 <minecraft-version>"
    echo "Example: $0 1.21.10"
    exit 1
fi

NEW_VERSION=$1

# Determine the upgrade-manager URL based on environment
UPGRADE_MANAGER_URL="${UPGRADE_MANAGER_URL:-http://localhost:8092}"

log_info "Triggering upgrade to version $NEW_VERSION..."
echo ""

# Make the API request
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$UPGRADE_MANAGER_URL/api/upgrades/trigger" \
  -H "Content-Type: application/json" \
  -d "{\"newVersion\":\"$NEW_VERSION\"}" 2>&1)

# Extract HTTP status code and response body
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

echo ""

# Check HTTP status code
if [ "$HTTP_CODE" = "200" ]; then
    log_info "Upgrade request successful!"
    echo ""
    echo "Response:"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
    echo ""
    log_info "Upgrade is in progress. This may take 10-15 minutes."
    log_info "Monitor logs: docker logs -f open-mc-upgrade-manager"
else
    log_error "Upgrade request failed with HTTP status $HTTP_CODE"
    echo ""
    echo "Response:"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
    exit 1
fi
