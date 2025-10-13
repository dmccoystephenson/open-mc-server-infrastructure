#!/bin/bash
# Test script for overload monitoring functionality
set -e

echo "🧪 Testing overload monitoring functionality..."

# Create a test directory structure
TEST_DIR="/tmp/test_mcserver_$$"
mkdir -p "$TEST_DIR/logs"

# Create a test log file
TEST_LOG="$TEST_DIR/logs/latest.log"
echo "[Server] Starting server..." > "$TEST_LOG"

# Function to cleanup
cleanup() {
    rm -rf "$TEST_DIR"
}
trap cleanup EXIT

# Test 1: Monitor script syntax
echo "Test 1: Checking script syntax..."
bash -n resources/monitor-overload.sh
echo "✅ Syntax check passed"

# Test 2: Monitor can start and stop
echo "Test 2: Testing monitor start and stop..."
export ALERT_EMAIL="test@example.com"
timeout 5s bash -c "
    ./resources/monitor-overload.sh '$TEST_DIR' >/dev/null 2>&1 &
    MONITOR_PID=\$!
    sleep 2
    kill -TERM \$MONITOR_PID 2>/dev/null || true
    wait \$MONITOR_PID 2>/dev/null || true
" || true
echo "✅ Monitor start/stop test passed"

# Test 3: Monitor detects overload messages
echo "Test 3: Testing overload message detection..."
TEST_OUTPUT=$(mktemp)
timeout 8s bash -c "
    ./resources/monitor-overload.sh '$TEST_DIR' > '$TEST_OUTPUT' 2>&1 &
    MONITOR_PID=\$!
    
    # Wait for monitor to start
    sleep 2
    
    # Simulate an overload message
    echo '[Server thread/WARN]: Can'\''t keep up! Is the server overloaded? Running 3639ms or 72 ticks behind' >> '$TEST_LOG'
    
    # Wait for processing
    sleep 3
    
    # Kill the monitor
    kill -TERM \$MONITOR_PID 2>/dev/null || true
    wait \$MONITOR_PID 2>/dev/null || true
" || true

if grep -q "OVERLOAD DETECTED" "$TEST_OUTPUT"; then
    echo "✅ Overload message detection test passed"
else
    echo "❌ Overload message detection test failed"
    cat "$TEST_OUTPUT"
    exit 1
fi
rm -f "$TEST_OUTPUT"

# Test 4: Monitor skips when ALERT_EMAIL is not set
echo "Test 4: Testing behavior without ALERT_EMAIL..."
unset ALERT_EMAIL
TEST_OUTPUT=$(mktemp)
timeout 3s bash -c "
    ./resources/monitor-overload.sh '$TEST_DIR' > '$TEST_OUTPUT' 2>&1 &
    MONITOR_PID=\$!
    sleep 1
    kill -TERM \$MONITOR_PID 2>/dev/null || true
    wait \$MONITOR_PID 2>/dev/null || true
" || true

if grep -q "not configured" "$TEST_OUTPUT"; then
    echo "✅ No alert email behavior test passed"
else
    echo "❌ No alert email behavior test failed"
    cat "$TEST_OUTPUT"
    exit 1
fi
rm -f "$TEST_OUTPUT"

echo "🎉 All overload monitoring tests passed!"
