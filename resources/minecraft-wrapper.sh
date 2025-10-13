#!/bin/bash
# Minecraft Server Wrapper - Handles graceful shutdown for plugin data preservation
# Based on proven patterns from docker-mc-lifecycle.sh
set -euo pipefail

# Function: Log with timestamp - ensure visibility in Docker logs
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WRAPPER] $1"
}

# Variables
SERVER_JAR="$1"
SERVER_DIR="$2" 
JAVA_OPTS="$3"
PID=""
MONITOR_PID=""
INPUT_FIFO="$SERVER_DIR/server_input"

# Function: Graceful shutdown  
# shellcheck disable=SC2317  # Function called via signal trap
graceful_shutdown() {
    log "Received shutdown signal, initiating graceful server stop..."
    
    # Stop the monitor if it's running
    if [ -n "$MONITOR_PID" ] && kill -0 "$MONITOR_PID" 2>/dev/null; then
        log "Stopping overload monitor..."
        kill -TERM "$MONITOR_PID" 2>/dev/null || true
    fi
    
    if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
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
# shellcheck disable=SC2086  # Word splitting is intentional for JAVA_OPTS
java $JAVA_OPTS -jar "$SERVER_JAR" nogui < "$INPUT_FIFO" &
PID=$!

log "Minecraft server started with PID: $PID"

# Start overload monitoring if ALERT_EMAIL is configured
if [ -n "${ALERT_EMAIL:-}" ]; then
    log "Starting overload monitor..."
    /resources/monitor-overload.sh "$SERVER_DIR" &
    MONITOR_PID=$!
    log "Overload monitor started with PID: $MONITOR_PID"
else
    log "ALERT_EMAIL not configured, skipping overload monitoring"
fi

# Wait until the server process finishes or a termination signal is received
wait "$PID"
EXIT_CODE=$?

# Clean up monitor if running
if [ -n "$MONITOR_PID" ] && kill -0 "$MONITOR_PID" 2>/dev/null; then
    log "Stopping overload monitor..."
    kill -TERM "$MONITOR_PID" 2>/dev/null || true
    wait "$MONITOR_PID" 2>/dev/null || true
fi

# Clean up FIFO keeper
kill "$FIFO_KEEPER_PID" 2>/dev/null || true

log "Minecraft server process exited with code: $EXIT_CODE"
exit $EXIT_CODE
