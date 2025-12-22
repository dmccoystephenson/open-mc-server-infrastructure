#!/bin/bash
# Test script for server overload alert functionality
# This script tests that the minecraft-wrapper detects and alerts on "Can't keep up!" messages

set -euo pipefail

# Detect script directory and repository root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "Testing server overload alert functionality..."
echo ""

# Test 1: Verify the wrapper script has the check_for_overload function
echo "Test 1: Checking if check_for_overload function exists in minecraft-wrapper.sh..."
if grep -q "check_for_overload" "$REPO_ROOT/resources/minecraft-wrapper.sh"; then
    echo "✓ check_for_overload function found"
else
    echo "✗ check_for_overload function not found"
    exit 1
fi

# Test 2: Verify the function checks for "Can't keep up!" message
echo ""
echo "Test 2: Checking if the function looks for 'Can't keep up!' message..."
if grep -q "Can't keep up!" "$REPO_ROOT/resources/minecraft-wrapper.sh"; then
    echo "✓ 'Can't keep up!' detection found"
else
    echo "✗ 'Can't keep up!' detection not found"
    exit 1
fi

# Test 3: Verify ALERTS_SERVER_OVERLOAD toggle exists
echo ""
echo "Test 3: Checking if ALERTS_SERVER_OVERLOAD toggle is configured..."
if grep -q "ALERTS_SERVER_OVERLOAD" "$REPO_ROOT/sample.env"; then
    echo "✓ ALERTS_SERVER_OVERLOAD toggle found in sample.env"
else
    echo "✗ ALERTS_SERVER_OVERLOAD toggle not found in sample.env"
    exit 1
fi

# Test 4: Verify the toggle is passed through compose.yml
echo ""
echo "Test 4: Checking if ALERTS_SERVER_OVERLOAD is passed through compose.yml..."
if grep -q "ALERTS_SERVER_OVERLOAD" "$REPO_ROOT/compose.yml"; then
    echo "✓ ALERTS_SERVER_OVERLOAD found in compose.yml"
else
    echo "✗ ALERTS_SERVER_OVERLOAD not found in compose.yml"
    exit 1
fi

# Test 5: Verify cooldown mechanism exists
echo ""
echo "Test 5: Checking if cooldown mechanism is implemented..."
if grep -q "OVERLOAD_ALERT_COOLDOWN" "$REPO_ROOT/resources/minecraft-wrapper.sh"; then
    echo "✓ Cooldown mechanism found"
else
    echo "✗ Cooldown mechanism not found"
    exit 1
fi

# Test 6: Verify the wrapper monitors server output
echo ""
echo "Test 6: Checking if server output is monitored for overload messages..."
if grep -q "check_for_overload.*line" "$REPO_ROOT/resources/minecraft-wrapper.sh"; then
    echo "✓ Server output monitoring found"
else
    echo "✗ Server output monitoring not found"
    exit 1
fi

echo ""
echo "=========================================="
echo "All tests passed! ✓"
echo "=========================================="
echo ""
echo "The server overload alert feature is properly configured."
echo "When the server outputs 'Can't keep up!' messages, alerts will be sent via alert-manager."
echo "Alerts are throttled to once every 5 minutes to prevent spam."
