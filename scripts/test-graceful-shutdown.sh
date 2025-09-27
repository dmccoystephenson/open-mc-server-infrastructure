#!/bin/bash
# Test script to verify graceful shutdown functionality
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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

# Cleanup function
# shellcheck disable=SC2317  # Function called via signal trap
cleanup() {
    test_log "Cleaning up test environment..."
    pkill -f "mock-minecraft-server" 2>/dev/null || true
    rm -rf /tmp/graceful-shutdown-test
    test_log "Cleanup completed"
}

# Set up cleanup trap
trap cleanup EXIT

test_log "🚀 Starting graceful shutdown test..."

# Create test environment
TEST_DIR="/tmp/graceful-shutdown-test"
mkdir -p "$TEST_DIR"

# Create mock Minecraft server that responds to commands
cat > "$TEST_DIR/mock-minecraft-server.jar" <<'EOF'
#!/bin/bash
echo "[MOCK-SERVER] Mock Minecraft server starting..."
echo "[MOCK-SERVER] Server started on localhost:25565"
echo "[MOCK-SERVER] Ready for connections!"

# Simulate server reading commands from stdin
while read -r line; do
    echo "[MOCK-SERVER] Command received: '$line'"
    case "$line" in
        "stop")
            echo "[MOCK-SERVER] Stopping server gracefully..."
            echo "[MOCK-SERVER] Saving world data..."
            echo "[MOCK-SERVER] Disabling plugins..."
            echo "[MOCK-SERVER] SimpleSkills: Saving player data..."
            sleep 2  # Simulate plugin save time
            echo "[MOCK-SERVER] SimpleSkills: Data saved successfully!"
            echo "[MOCK-SERVER] Server stopped."
            exit 0
            ;;
        *)
            echo "[MOCK-SERVER] Unknown command: $line"
            ;;
    esac
done

echo "[MOCK-SERVER] Input stream closed, stopping server..."
exit 0
EOF

# Create mock java executable that can run our mock server
cat > "$TEST_DIR/java" <<'EOF'
#!/bin/bash
# Mock java that executes JAR files

# Find the -jar parameter and execute the jar
for ((i=1; i<=$#; i++)); do
    if [ "${!i}" = "-jar" ]; then
        j=$((i+1))
        JAR_FILE="${!j}"
        # Make jar path absolute if needed
        if [[ "$JAR_FILE" != /* ]]; then
            JAR_FILE="$PWD/$JAR_FILE"
        fi
        # Skip to arguments after jar file
        shift $j
        exec "$JAR_FILE" "$@"
    fi
done

echo "Mock java: No -jar parameter found"
exit 1
EOF

chmod +x "$TEST_DIR/mock-minecraft-server.jar" "$TEST_DIR/java"

# Test 1: Verify wrapper starts correctly
test_log "Test 1: Verifying wrapper startup..."
cd "$(dirname "$0")/.."  # Go to repo root
export PATH="$TEST_DIR:$PATH"

# Start wrapper in background and capture output
./resources/minecraft-wrapper.sh mock-minecraft-server.jar "$TEST_DIR" "-Xmx1G" > "$TEST_DIR/wrapper_output.log" 2>&1 &
WRAPPER_PID=$!

# Give it time to start
sleep 5

# Check if wrapper is running
if ! kill -0 "$WRAPPER_PID" 2>/dev/null; then
    test_error "Wrapper failed to start"
    cat "$TEST_DIR/wrapper_output.log"
    exit 1
fi

test_success "Wrapper started successfully"

# Test 2: Verify wrapper logs are visible
test_log "Test 2: Checking wrapper startup logs..."
if grep -q "\[WRAPPER\] Starting Minecraft server with wrapper" "$TEST_DIR/wrapper_output.log"; then
    test_success "Wrapper startup logs are visible"
else
    test_error "Wrapper startup logs not found"
    cat "$TEST_DIR/wrapper_output.log"
    exit 1
fi

# Test 3: Verify server logs are visible
test_log "Test 3: Checking server startup logs..."
sleep 3  # Give server more time to start
if grep -q "\[MOCK-SERVER\] Mock Minecraft server starting" "$TEST_DIR/wrapper_output.log"; then
    test_success "Server startup logs are visible"
else
    test_error "Server startup logs not found"
    cat "$TEST_DIR/wrapper_output.log"
    exit 1
fi

# Test 4: Test graceful shutdown
test_log "Test 4: Testing graceful shutdown with SIGTERM..."

# Give the server more time to fully stabilize
sleep 2

# Send SIGTERM to wrapper (simulating docker compose stop)
kill -TERM "$WRAPPER_PID"

# Wait for shutdown to complete
sleep 10

# Check wrapper output for shutdown logs
test_log "Checking for shutdown logs in wrapper output..."
cat "$TEST_DIR/wrapper_output.log"

# Verify shutdown sequence
SHUTDOWN_TESTS=0
SHUTDOWN_PASSED=0

if grep -q "\[WRAPPER\] Received shutdown signal" "$TEST_DIR/wrapper_output.log"; then
    test_success "✓ Shutdown signal received by wrapper"
    SHUTDOWN_PASSED=$((SHUTDOWN_PASSED + 1))
else
    test_error "✗ Shutdown signal not received by wrapper"
fi
SHUTDOWN_TESTS=$((SHUTDOWN_TESTS + 1))

if grep -q "\[WRAPPER\] Sending 'stop' command to Minecraft server" "$TEST_DIR/wrapper_output.log"; then
    test_success "✓ Stop command sent to server"
    SHUTDOWN_PASSED=$((SHUTDOWN_PASSED + 1))
else
    test_error "✗ Stop command not sent to server"
fi
SHUTDOWN_TESTS=$((SHUTDOWN_TESTS + 1))

if grep -q "\[MOCK-SERVER\] Stopping server gracefully" "$TEST_DIR/wrapper_output.log"; then
    test_success "✓ Server received stop command"
    SHUTDOWN_PASSED=$((SHUTDOWN_PASSED + 1))
else
    test_error "✗ Server did not receive stop command"
fi
SHUTDOWN_TESTS=$((SHUTDOWN_TESTS + 1))

if grep -q "\[MOCK-SERVER\] SimpleSkills: Data saved successfully" "$TEST_DIR/wrapper_output.log"; then
    test_success "✓ Plugin data saved during graceful shutdown"
    SHUTDOWN_PASSED=$((SHUTDOWN_PASSED + 1))
else
    test_error "✗ Plugin data not saved during shutdown"
fi
SHUTDOWN_TESTS=$((SHUTDOWN_TESTS + 1))

if grep -q "\[WRAPPER\] Server shutdown gracefully" "$TEST_DIR/wrapper_output.log"; then
    test_success "✓ Wrapper confirmed graceful shutdown"
    SHUTDOWN_PASSED=$((SHUTDOWN_PASSED + 1))
else
    test_error "✗ Wrapper did not confirm graceful shutdown"
fi
SHUTDOWN_TESTS=$((SHUTDOWN_TESTS + 1))

# Final results
test_log "📊 Test Results:"
test_log "Shutdown tests passed: $SHUTDOWN_PASSED/$SHUTDOWN_TESTS"

if [ "$SHUTDOWN_PASSED" -eq "$SHUTDOWN_TESTS" ]; then
    test_success "🎉 All graceful shutdown tests passed!"
    test_success "The wrapper correctly handles SIGTERM and preserves plugin data"
    exit 0
else
    test_error "❌ Some graceful shutdown tests failed"
    test_error "The wrapper may not be working correctly in Docker environments"
    exit 1
fi