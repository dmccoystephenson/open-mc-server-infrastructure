#!/bin/bash
# Test script to demonstrate rate limiting functionality

ALERT_MANAGER_URL="${ALERT_MANAGER_URL:-http://localhost:8090}"
CONTAINER_NAME="${ALERT_CONTAINER_NAME:-open-mc-alert-manager}"

echo "Testing Alert Rate Limiting"
echo "=============================="
echo "Configuration (can be customized in .env):"
echo "  - ALERT_RATE_LIMIT_ENABLED: true (default)"
echo "  - ALERT_RATE_LIMIT_MAX_ALERTS: 10 (default)"
echo "  - ALERT_RATE_LIMIT_WINDOW_SECONDS: 60 (default)"
echo ""
echo "This test will send 15 alerts rapidly to demonstrate rate limiting."
echo "The first 10 should succeed, and the remaining 5 should be rate limited."
echo ""

# Function to send an alert
send_alert() {
    local num=$1
    echo "Sending alert $num..."
    
    response=$(curl -s -X POST "$ALERT_MANAGER_URL/api/alerts" \
        -H "Content-Type: application/json" \
        -d "{
            \"title\": \"Test Alert $num\",
            \"message\": \"This is test alert number $num to demonstrate rate limiting\",
            \"level\": \"INFO\",
            \"source\": \"rate-limit-test\",
            \"destinations\": [\"DISCORD\"]
        }")
    
    echo "  Response: $response"
}

# Send 15 alerts rapidly
for i in {1..15}; do
    send_alert $i
    sleep 0.5  # Small delay between requests
done

echo ""
echo "Test complete!"
echo "Check the alert-manager logs to see rate limiting in action:"
echo "  docker logs $CONTAINER_NAME | grep -i \"rate\""
echo ""
echo "The logs should show messages like:"
echo "  'Alert rate limited for destination: DISCORD. Skipping alert: Test Alert X'"
echo ""
echo "Note: To test with different rate limits, modify the values in your .env file:"
echo "  ALERT_RATE_LIMIT_MAX_ALERTS=5  # Lower limit for testing"
echo "  ALERT_RATE_LIMIT_WINDOW_SECONDS=30  # Shorter window for testing"
echo "  ALERT_CONTAINER_NAME=custom-name  # Custom container name"
