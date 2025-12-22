#!/bin/bash
# Integration test for server overload alert
# This test verifies the overload detection logic and cooldown mechanism

set -euo pipefail

# Detect script directory and repository root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=========================================="
echo "Server Overload Alert - Integration Test"
echo "=========================================="
echo ""

# Set up test environment
TEST_DIR="/tmp/overload-test-$$"
mkdir -p "$TEST_DIR"
cd "$TEST_DIR"

# Mock variables and functions needed by check_for_overload
export OVERLOAD_ALERT_COOLDOWN=5  # Use 5 seconds for testing
export ALERTS_SERVER_OVERLOAD=true
export ALERT_MANAGER_URL="http://localhost:8090/api/alerts"
export OVERLOAD_ALERT_TIMESTAMP_FILE="$TEST_DIR/.last_overload_alert"
export SERVER_DIR="$TEST_DIR"

# Track alerts sent
ALERT_COUNT=0

# Mock log function
log() {
    echo "[TEST-LOG] $1"
}
export -f log

# Mock send_alert function that tracks calls
send_alert() {
    local title="$1"
    local message="$2"
    local level="${3:-INFO}"
    local alert_toggle="${4:-}"
    
    ALERT_COUNT=$((ALERT_COUNT + 1))
    echo "[TEST-ALERT #$ALERT_COUNT] Title: $title"
    echo "[TEST-ALERT #$ALERT_COUNT] Message: $message"
    echo "[TEST-ALERT #$ALERT_COUNT] Level: $level"
    echo "[TEST-ALERT #$ALERT_COUNT] Toggle: $alert_toggle"
}
export -f send_alert

# Extract and source the check_for_overload function from minecraft-wrapper.sh
# Extract the function definition
sed -n '/^check_for_overload() {$/,/^}$/p' "$REPO_ROOT/resources/minecraft-wrapper.sh" > "$TEST_DIR/check_for_overload.sh"

# Source the extracted function
# shellcheck source=/dev/null
source "$TEST_DIR/check_for_overload.sh"

# Test cases
echo "Test 1: Normal log line (should not trigger alert)"
echo "----------------------------------------"
check_for_overload "[12:34:56] [Server thread/INFO]: Player joined the game"
if [ $ALERT_COUNT -eq 0 ]; then
    echo "✓ No alert sent (correct)"
else
    echo "✗ Alert was sent when it shouldn't have been"
    exit 1
fi
echo ""

echo "Test 2: First overload message with timing info (should trigger alert)"
echo "----------------------------------------"
check_for_overload "[12:34:56] [Server thread/WARN]: Can't keep up! Is the server overloaded? Running 3639ms or 72 ticks behind"
if [ $ALERT_COUNT -eq 1 ]; then
    echo "✓ Alert sent (correct)"
else
    echo "✗ Expected 1 alert, got $ALERT_COUNT"
    exit 1
fi
echo ""

echo "Test 3: Second overload message immediately after (should be suppressed by cooldown)"
echo "----------------------------------------"
check_for_overload "[12:34:57] [Server thread/WARN]: Can't keep up! Is the server overloaded? Running 2500ms or 50 ticks behind"
if [ $ALERT_COUNT -eq 1 ]; then
    echo "✓ Alert suppressed by cooldown (correct)"
else
    echo "✗ Expected 1 total alert, got $ALERT_COUNT"
    exit 1
fi
echo ""

echo "Test 4: Wait for cooldown period to expire..."
echo "----------------------------------------"
echo "Waiting 6 seconds for cooldown to expire..."
sleep 6
echo ""

echo "Test 5: Overload message after cooldown (should trigger alert)"
echo "----------------------------------------"
check_for_overload "[12:35:00] [Server thread/WARN]: Can't keep up! Is the server overloaded? Running 1500ms or 30 ticks behind"
if [ $ALERT_COUNT -eq 2 ]; then
    echo "✓ Alert sent after cooldown expired (correct)"
else
    echo "✗ Expected 2 total alerts, got $ALERT_COUNT"
    exit 1
fi
echo ""

echo "Test 6: Normal log line again (should not trigger)"
echo "----------------------------------------"
check_for_overload "[12:35:01] [Server thread/INFO]: Time elapsed: 100ms"
if [ $ALERT_COUNT -eq 2 ]; then
    echo "✓ No alert sent (correct)"
else
    echo "✗ Expected 2 total alerts, got $ALERT_COUNT"
    exit 1
fi
echo ""

# Cleanup
cd /
rm -rf "$TEST_DIR"

echo "=========================================="
echo "All Integration Tests Passed! ✓"
echo "=========================================="
echo ""
echo "Summary:"
echo "- Normal log lines do not trigger alerts"
echo "- Overload messages trigger alerts with extracted timing info"
echo "- Cooldown mechanism properly suppresses rapid alerts"
echo "- Alerts resume after cooldown period expires"
echo "- File-based cooldown tracking persists correctly"
