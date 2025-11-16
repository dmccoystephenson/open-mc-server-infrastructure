#!/bin/bash
# Test script to verify crash alert is sent when server exits with non-zero code
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
    rm -rf /tmp/crash-alert-test
    test_log "Cleanup completed"
}

# Set up cleanup trap
trap cleanup EXIT

test_log "🚀 Starting crash alert test..."

# Create test environment
TEST_DIR="/tmp/crash-alert-test"
mkdir -p "$TEST_DIR"

# Create mock Minecraft server that crashes with non-zero exit code
cat > "$TEST_DIR/mock-minecraft-server.jar" <<'EOF'
#!/bin/bash
echo "[MOCK-SERVER] Mock Minecraft server starting..."
echo "[MOCK-SERVER] Server started on localhost:25565"
echo "[MOCK-SERVER] Ready for connections!"

# Simulate server reading commands from stdin but crash after a short time
(
    sleep 2
    # Send a signal to the main process to simulate crash
    kill -USR1 $$
) &

# Set up trap to handle the crash signal
trap '
    echo "[MOCK-SERVER] ERROR: Out of memory!"
    echo "[MOCK-SERVER] Server crashed!"
    exit 1
' USR1

# Read from stdin to keep process alive (like real Minecraft server)
while read -r line; do
    echo "[MOCK-SERVER] Command received: '"'"'$line'"'"'"
done

# This should never be reached
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

# Test: Verify wrapper sends crash alert when server crashes
test_log "Test: Verifying wrapper sends crash alert when server crashes..."
cd "$(dirname "$0")/.."  # Go to repo root
export PATH="$TEST_DIR:$PATH"

# Start wrapper in background and capture output
./resources/minecraft-wrapper.sh mock-minecraft-server.jar "$TEST_DIR" "-Xmx1G" > "$TEST_DIR/wrapper_output.log" 2>&1 &
WRAPPER_PID=$!

# Give it time to start and crash
sleep 5

# Wait for wrapper to finish
wait "$WRAPPER_PID" 2>/dev/null || true

# Check wrapper output for crash alert
test_log "Checking for crash alert in wrapper output..."
cat "$TEST_DIR/wrapper_output.log"

# Verify crash alert was sent
TESTS_PASSED=0
TESTS_TOTAL=0

if grep -q "\[WRAPPER\] Minecraft server process exited with code: 1" "$TEST_DIR/wrapper_output.log"; then
    test_success "✓ Server exited with code 1 (crash)"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ Server did not exit with code 1"
fi
TESTS_TOTAL=$((TESTS_TOTAL + 1))

if grep -q "\[WRAPPER\] Sending alert to.*Minecraft Server Crashed" "$TEST_DIR/wrapper_output.log"; then
    test_success "✓ Crash alert was sent when server crashed"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ Crash alert was NOT sent when server crashed"
fi
TESTS_TOTAL=$((TESTS_TOTAL + 1))

if grep -q "exited unexpectedly with code 1" "$TEST_DIR/wrapper_output.log"; then
    test_success "✓ Crash alert contains correct error details"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ Crash alert does not contain correct error details"
fi
TESTS_TOTAL=$((TESTS_TOTAL + 1))

# Verify that normal shutdown alert was NOT sent (since exit code was non-zero)
if ! grep -q "\[WRAPPER\] Sending alert to.*Minecraft Server Stopped" "$TEST_DIR/wrapper_output.log" || ! grep -q "has been shut down\." "$TEST_DIR/wrapper_output.log"; then
    test_success "✓ Normal shutdown alert was NOT sent (correct behavior for crash)"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    test_error "✗ Normal shutdown alert was incorrectly sent for crash"
fi
TESTS_TOTAL=$((TESTS_TOTAL + 1))

# Final results
test_log "📊 Test Results:"
test_log "Tests passed: $TESTS_PASSED/$TESTS_TOTAL"

if [ "$TESTS_PASSED" -eq "$TESTS_TOTAL" ]; then
    test_success "🎉 All crash alert tests passed!"
    test_success "The wrapper correctly sends crash alert when server crashes"
    exit 0
else
    test_error "❌ Some crash alert tests failed"
    test_error "The wrapper is not sending the correct alert when server crashes"
    exit 1
fi
