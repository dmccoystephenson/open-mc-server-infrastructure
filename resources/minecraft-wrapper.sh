#!/bin/bash
# Minecraft Server Wrapper - Handles graceful shutdown for plugin data preservation
# Based on proven patterns from itzg/mc-server-runner and docker-mc-lifecycle.sh
set -euo pipefail

# Function: Log with timestamp
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WRAPPER] $1" >&2
}

# Variables
SERVER_JAR="$1"
SERVER_DIR="$2" 
JAVA_OPTS="$3"
PID=""

# Function: Graceful shutdown  
graceful_shutdown() {
    log "Received shutdown signal, initiating graceful server stop..."
    
    if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
        log "Sending 'stop' command to Minecraft server..."
        
        # This is the key - we use echo with a newline to send the stop command
        echo "stop" >&3 || {
            log "Failed to send stop command, sending SIGTERM..."
            kill -TERM "$PID"
        }
        
        # Wait for server to shutdown gracefully (up to 30 seconds)
        local count=0
        while [ $count -lt 30 ] && kill -0 "$PID" 2>/dev/null; do
            sleep 1
            count=$((count + 1))
            log "Waiting for server shutdown... ($count/30)"
        done
        
        # Force kill if still running
        if kill -0 "$PID" 2>/dev/null; then
            log "Server didn't shutdown gracefully, forcing termination..."
            kill -KILL "$PID"
        else
            log "Server shutdown gracefully."
        fi
    else
        log "No server process found or already terminated."
    fi
    
    # Close the stdin pipe
    exec 3>&- 2>/dev/null || true
    
    exit 0
}

# Set up signal handlers
trap graceful_shutdown SIGTERM SIGINT

# Start Minecraft server
log "Starting Minecraft server with wrapper..."
log "Server JAR: $SERVER_JAR"
log "Server Directory: $SERVER_DIR"
log "Java Options: $JAVA_OPTS"

cd "$SERVER_DIR" || {
    log "ERROR: Cannot change to server directory: $SERVER_DIR"
    exit 1
}

# Start the server with a stdin pipe on file descriptor 3
# This approach is based on itzg/mc-server-runner
log "Starting Minecraft server..."

{
    # Start java process with stdin from file descriptor 3
    java $JAVA_OPTS -jar "$SERVER_JAR" nogui <&3 &
    PID=$!
    
    log "Minecraft server started with PID: $PID"
    
    # Wait for the server process to finish
    wait "$PID"
    EXIT_CODE=$?
    
    log "Minecraft server process exited with code: $EXIT_CODE"
    exit $EXIT_CODE
} 3< <(
    # This creates the input stream for the server
    # It will wait for input and forward it to the server
    while true; do
        sleep 1000  # Keep the pipe open
    done
)