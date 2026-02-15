#!/bin/bash
# Test script to verify update-accord.sh functionality
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to log test output
test_log() {
    echo -e "${YELLOW}[TEST]${NC} $1"
}

test_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

test_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

test_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

# Cleanup function
# shellcheck disable=SC2317  # Function called via signal trap
cleanup() {
    test_log "Cleaning up test environment..."
    rm -rf /tmp/accord-update-test
    test_log "Cleanup completed"
}

# Set up cleanup trap
trap cleanup EXIT

echo "=========================================="
echo "  Accord Update Script Tests"
echo "=========================================="
echo ""

# Test 1: Check if script exists and is executable
test_log "Test 1: Verifying update-accord.sh exists and is executable..."
cd "$(dirname "$0")/.."  # Go to repo root

if [ ! -f "update-accord.sh" ]; then
    test_error "update-accord.sh not found"
    exit 1
fi

if [ ! -x "update-accord.sh" ]; then
    test_error "update-accord.sh is not executable"
    exit 1
fi

test_success "update-accord.sh exists and is executable"
echo ""

# Test 2: Verify help output
test_log "Test 2: Verifying help output..."
if ./update-accord.sh --help | grep -q "Usage:"; then
    test_success "Help message displays correctly"
else
    test_error "Help message not displaying correctly"
    exit 1
fi

# Verify --yes flag is documented
if ./update-accord.sh --help | grep -q "\-y, \-\-yes"; then
    test_success "Non-interactive mode documented"
else
    test_error "--yes flag not documented"
    exit 1
fi
echo ""

# Test 3: Verify script handles invalid arguments
test_log "Test 3: Verifying invalid argument handling..."
# We expect the script to fail, so capture both output and ignore failure
set +e
output=$(./update-accord.sh --invalid-option 2>&1)
set -e
if echo "$output" | grep -q "Unknown option"; then
    test_success "Script correctly handles invalid arguments"
else
    test_error "Script does not handle invalid arguments correctly"
    exit 1
fi
echo ""

# Test 4: Test accord-chat .env creation
test_log "Test 4: Testing accord-chat .env file handling..."
TEST_DIR="/tmp/accord-update-test"
mkdir -p "$TEST_DIR/accord-chat"

# Create a mock accord-chat/sample.env
cat > "$TEST_DIR/accord-chat/sample.env" <<'EOF'
# Test environment variables
TEST_VAR_1=value1
TEST_VAR_2=value2
EOF

# Test that function exists
if grep -q "^ensure_accord_env()" update-accord.sh; then
    test_success "Accord .env handling function exists"
else
    test_error "Accord .env handling function not found"
    exit 1
fi
echo ""

# Test 5: Verify the script can be sourced for function testing
test_log "Test 5: Verifying script structure for testability..."

# Extract function definitions to verify they exist
if grep -q "^check_submodule()" update-accord.sh && \
   grep -q "^ensure_accord_env()" update-accord.sh && \
   grep -q "^pull_updates()" update-accord.sh && \
   grep -q "^restart_accord_services()" update-accord.sh; then
    test_success "All required functions are present in the script"
else
    test_error "Some required functions are missing from the script"
    exit 1
fi
echo ""

# Test 6: Verify script validates branch existence (can't test fully without git setup)
test_log "Test 6: Verifying script structure for branch validation..."
if grep -q "git rev-parse --verify" update-accord.sh && \
   grep -q "Branch.*does not exist" update-accord.sh; then
    test_success "Script includes branch validation logic"
else
    test_error "Script missing branch validation"
    exit 1
fi
echo ""

# Test 7: Verify Docker Compose commands are present
test_log "Test 7: Verifying Docker Compose integration..."
if grep -q "docker compose stop accord-backend accord-webapp" update-accord.sh && \
   grep -q "docker compose up -d --build accord-backend accord-webapp" update-accord.sh; then
    test_success "Docker Compose commands are correctly integrated"
else
    test_error "Docker Compose commands are missing or incorrect"
    exit 1
fi
echo ""

# Final results
echo "=========================================="
test_success "🎉 All update-accord.sh tests passed!"
echo "=========================================="
echo ""
test_info "The update-accord.sh script is ready for use"
test_info "Run './update-accord.sh --help' for usage instructions"
echo ""

exit 0
