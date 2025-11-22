#!/bin/bash
# Minecraft Server Wrapper - Handles graceful shutdown for plugin data preservation
# Based on proven patterns from docker-mc-lifecycle.sh
set -euo pipefail

# Function: Log with timestamp - ensure visibility in Docker logs
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WRAPPER] $1"
}

# Function: Send alert to alert-manager
send_alert() {
    local title="$1"
    local message="$2"
    local level="${3:-INFO}"
    local alert_toggle="${4:-}"
    local destinations="${5:-[\"DISCORD\"]}"  # Default to Discord only for lifecycle alerts
    local source="minecraft-server"
    
    # Check if this type of alert is enabled (if toggle variable is provided)
    if [ -n "$alert_toggle" ]; then
        local toggle_value="${!alert_toggle:-true}"
        if [ "$toggle_value" != "true" ]; then
            log "Alert skipped (disabled via $alert_toggle): $title"
            return 0
        fi
    fi
    
    local alert_url="${ALERT_MANAGER_URL:-http://alert-manager:8090/api/alerts}"
    
    # Try to send alert, but don't fail if it doesn't work
    if command -v curl >/dev/null 2>&1; then
        log "Sending alert to $alert_url: $title ($level)"
        
        # Capture HTTP response code and any error output
        local http_code
        local curl_output
        curl_output=$(curl -X POST "$alert_url" \
          -H "Content-Type: application/json" \
          -w "\n%{http_code}" \
          --max-time 5 \
          --connect-timeout 5 \
          -d "{\"title\":\"$title\",\"message\":\"$message\",\"level\":\"$level\",\"source\":\"$source\",\"destinations\":$destinations}" \
          2>&1 || echo "CURL_FAILED")
        
        http_code=$(echo "$curl_output" | tail -1)
        
        if [ "$curl_output" = "CURL_FAILED" ]; then
            log "Alert failed: curl command failed (connection error or timeout)"
        elif [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
            log "Alert sent successfully (HTTP $http_code)"
        else
            log "Alert failed: HTTP $http_code"
        fi
    else
        log "curl not available, skipping alert: $title"
    fi
}

# Function: Send message to players via alert-manager
# shellcheck disable=SC2317  # Function called from graceful_shutdown
send_message() {
    local text="$1"
    local destination="${2:-MINECRAFT}"
    
    local alert_url="${ALERT_MANAGER_URL:-http://alert-manager:8090}/api/alerts"
    
    # Try to send message, but don't fail if it doesn't work
    if command -v curl >/dev/null 2>&1; then
        log "Sending message to $alert_url: $text"
        
        # Construct JSON array for destinations (uppercase for enum)
        local destinations_json="[\"$destination\"]"
        
        # Capture HTTP response code and any error output
        local http_code
        local curl_output
        curl_output=$(curl -X POST "$alert_url" \
          -H "Content-Type: application/json" \
          -w "\n%{http_code}" \
          --max-time 5 \
          --connect-timeout 5 \
          -d "{\"message\":\"$text\",\"destinations\":$destinations_json,\"source\":\"minecraft-server\",\"level\":\"INFO\"}" \
          2>&1 || echo "CURL_FAILED")
        
        http_code=$(echo "$curl_output" | tail -1)
        
        if [ "$curl_output" = "CURL_FAILED" ]; then
            log "Message failed: curl command failed (connection error or timeout)"
        elif [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
            log "Message sent successfully (HTTP $http_code)"
        else
            log "Message failed: HTTP $http_code"
        fi
    else
        log "curl not available, skipping message: $text"
    fi
}

# Variables
SERVER_JAR="$1"
SERVER_DIR="$2" 
JAVA_OPTS="$3"
SERVER_TYPE="${4:-spigot}"
PID=""
INPUT_FIFO="$SERVER_DIR/server_input"

# Function: Graceful shutdown  
# shellcheck disable=SC2317  # Function called via signal trap
graceful_shutdown() {
    log "Received shutdown signal, initiating graceful server stop..."
    
    if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
        # Warn players before shutdown with countdown via alert-manager
        log "Warning players of impending shutdown..."
        
        send_message "Server is shutting down in 30 seconds!" "MINECRAFT"
        sleep 10
        
        send_message "Server is shutting down in 20 seconds!" "MINECRAFT"
        sleep 10
        
        send_message "Server is shutting down in 10 seconds!" "MINECRAFT"
        sleep 5
        
        send_message "Server is shutting down in 5 seconds!" "MINECRAFT"
        sleep 5
        
        log "Sending 'stop' command to Minecraft server..."
        
        # Send stop command to the server via the FIFO
        echo "stop" > "$INPUT_FIFO" 2>/dev/null || {
            log "Failed to send stop command via FIFO, sending SIGTERM..."
            kill -TERM "$PID"
        }
        
        # Wait for the server to shut down gracefully
        log "Waiting for server to shutdown gracefully..."
        wait "$PID" 2>/dev/null || true
        
        log "Server shutdown gracefully"
        
        # Send alert that server has stopped
        send_alert "Minecraft Server Stopped" "The Minecraft server has been shut down gracefully." "INFO" "ALERTS_SERVER_STOP"
    else
        log "No server process found or already terminated."
    fi
    
    # Clean up FIFO
    [ -p "$INPUT_FIFO" ] && rm -f "$INPUT_FIFO"
    
    exit 0
}

# Function: Cleanup on exit
# shellcheck disable=SC2317  # Function called via signal trap
cleanup() {
    [ -p "$INPUT_FIFO" ] && rm -f "$INPUT_FIFO"
}

# Set up signal handlers
trap graceful_shutdown SIGTERM SIGINT
trap cleanup EXIT

# Start Minecraft server
log "Starting Minecraft server with wrapper..."
log "Server JAR: $SERVER_JAR"
log "Server Directory: $SERVER_DIR"
log "Java Options: $JAVA_OPTS"

cd "$SERVER_DIR" || {
    log "ERROR: Cannot change to server directory: $SERVER_DIR"
    exit 1
}

# Create a named pipe (FIFO) for passing commands to the server
[ -p "$INPUT_FIFO" ] && rm -f "$INPUT_FIFO"
mkfifo "$INPUT_FIFO"

# Keep the FIFO open by running a background process that feeds it
# This ensures the server doesn't block on stdin
{
    # Keep the FIFO open - the server will read from it
    while true; do
        sleep 3600  # Keep process alive to maintain FIFO
    done
} > "$INPUT_FIFO" &
FIFO_KEEPER_PID=$!

# Start the Minecraft server and attach stdin to the named pipe
log "Starting Minecraft server..."
log "Server Type: $SERVER_TYPE"

if [ "$SERVER_TYPE" = "forge" ] && [ "$SERVER_JAR" = "run.sh" ]; then
    # For Forge servers using run.sh, we need to modify the script to use our JAVA_OPTS
    # and ensure it uses the FIFO for input
    log "Starting Forge server using run.sh..."
    
    # Update user_jvm_args.txt with our JAVA_OPTS if it exists
    if [ -f "$SERVER_DIR/user_jvm_args.txt" ]; then
        echo "$JAVA_OPTS" > "$SERVER_DIR/user_jvm_args.txt"
    fi
    
    # Execute the run.sh script with FIFO as input
    bash "$SERVER_DIR/run.sh" < "$INPUT_FIFO" &
    PID=$!
else
    # For Spigot and direct JAR execution
    # shellcheck disable=SC2086  # Word splitting is intentional for JAVA_OPTS
    java $JAVA_OPTS -jar "$SERVER_JAR" nogui < "$INPUT_FIFO" &
    PID=$!
fi

log "Minecraft server started with PID: $PID"

# Send alert that server has started
send_alert "Minecraft Server Started" "The Minecraft server has started successfully." "INFO" "ALERTS_SERVER_START"

# Wait until the server process finishes or a termination signal is received
wait "$PID"
EXIT_CODE=$?

# Clean up FIFO keeper
kill "$FIFO_KEEPER_PID" 2>/dev/null || true

log "Minecraft server process exited with code: $EXIT_CODE"

# Send appropriate alert based on exit code
if [ $EXIT_CODE -eq 0 ]; then
    # Normal shutdown (e.g., /stop command) - send shutdown alert
    send_alert "Minecraft Server Stopped" "The Minecraft server has been shut down." "INFO" "ALERTS_SERVER_STOP"
else
    # Server crashed (non-zero exit code, not from graceful shutdown)
    send_alert "Minecraft Server Crashed" "The Minecraft server exited unexpectedly with code $EXIT_CODE. Check logs for details." "ERROR" "ALERTS_SERVER_CRASH"
fi

exit $EXIT_CODE
