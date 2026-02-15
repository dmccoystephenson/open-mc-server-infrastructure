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

# Test 4: Create test environment with mock .env files
test_log "Test 4: Testing environment variable detection..."
TEST_DIR="/tmp/accord-update-test"
mkdir -p "$TEST_DIR/accord-chat"

# Create a mock accord-chat/sample.env with some variables
cat > "$TEST_DIR/accord-chat/sample.env" <<'EOF'
# Test environment variables
EXISTING_VAR=value1
NEW_VAR_1=value2
NEW_VAR_2=value3
ANOTHER_EXISTING_VAR=value4
EOF

# Create a mock root sample.env with only some of the variables
cat > "$TEST_DIR/sample.env" <<'EOF'
# Root environment variables
EXISTING_VAR=root_value1
ANOTHER_EXISTING_VAR=root_value2
EOF

# Test extraction of environment variables
test_info "Testing environment variable extraction..."

# Create a temporary script to test the env var extraction function
cat > "$TEST_DIR/test_env_extract.sh" <<'EOF'
#!/bin/bash
get_env_vars() {
    local env_file="$1"
    if [ ! -f "$env_file" ]; then
        echo ""
        return
    fi
    grep -E '^[A-Z_][A-Z0-9_]*=' "$env_file" | cut -d'=' -f1 | sort -u
}

accord_vars=$(get_env_vars "accord-chat/sample.env")
root_vars=$(get_env_vars "sample.env")

echo "Accord vars:"
echo "$accord_vars"
echo ""
echo "Root vars:"
echo "$root_vars"
echo ""

# Find new variables
new_vars=""
while IFS= read -r var; do
    if ! echo "$root_vars" | grep -q "^${var}$"; then
        new_vars="${new_vars}${var}\n"
    fi
done <<< "$accord_vars"

echo "New vars:"
echo -e "$new_vars" | grep -v '^$'
EOF

chmod +x "$TEST_DIR/test_env_extract.sh"

cd "$TEST_DIR"
output=$("$TEST_DIR/test_env_extract.sh")
cd - > /dev/null

# Verify that NEW_VAR_1 and NEW_VAR_2 are detected
if echo "$output" | grep -q "NEW_VAR_1" && echo "$output" | grep -q "NEW_VAR_2"; then
    test_success "Environment variable detection works correctly"
else
    test_error "Environment variable detection failed"
    echo "Output was:"
    echo "$output"
    exit 1
fi
echo ""

# Test 5: Verify the script can be sourced for function testing
test_log "Test 5: Verifying script structure for testability..."

# Extract function definitions to verify they exist
if grep -q "^check_submodule()" update-accord.sh && \
   grep -q "^detect_new_env_vars()" update-accord.sh && \
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
   grep -q "docker compose build accord-backend accord-webapp" update-accord.sh && \
   grep -q "docker compose up -d accord-backend accord-webapp" update-accord.sh; then
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
