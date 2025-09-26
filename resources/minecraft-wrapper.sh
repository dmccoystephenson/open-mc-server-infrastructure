#!/bin/bash
# Minecraft Server Wrapper - Handles graceful shutdown for plugin data preservation
# Based on proven patterns from itzg/mc-server-runner
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
STDIN_PIPE=""

# Function: Graceful shutdown  
graceful_shutdown() {
    log "Received shutdown signal, initiating graceful server stop..."
    
    if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
        log "Sending 'stop' command to Minecraft server..."
        
        # Send stop command through the stdin pipe
        if [ -n "$STDIN_PIPE" ]; then
            echo "stop" > "$STDIN_PIPE" 2>/dev/null || {
                log "Failed to send stop command via pipe, sending SIGTERM..."
                kill -TERM "$PID"
            }
        else
            log "No stdin pipe available, sending SIGTERM..."
            kill -TERM "$PID"
        fi
        
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
    
    # Clean up the pipe
    [ -p "$STDIN_PIPE" ] && rm -f "$STDIN_PIPE"
    
    exit 0
}

# Function: Cleanup on exit
cleanup() {
    [ -p "$STDIN_PIPE" ] && rm -f "$STDIN_PIPE"
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

# Create a named pipe for server input
STDIN_PIPE="$SERVER_DIR/server_stdin"
[ -p "$STDIN_PIPE" ] && rm -f "$STDIN_PIPE"
mkfifo "$STDIN_PIPE"

# Keep the pipe open in the background
{
    while true; do
        sleep 3600  # Keep pipe open
    done
} > "$STDIN_PIPE" &
PIPE_KEEPER_PID=$!

# Start Minecraft server with stdin from the named pipe
log "Starting Minecraft server..."
java $JAVA_OPTS -jar "$SERVER_JAR" nogui < "$STDIN_PIPE" &
PID=$!

log "Minecraft server started with PID: $PID"

# Wait for the server process to finish using a loop that allows signal handling
log "Waiting for server process..."
while kill -0 "$PID" 2>/dev/null; do
    sleep 1
done

# Get exit code
wait "$PID" 2>/dev/null
EXIT_CODE=$?

# Clean up pipe keeper
kill $PIPE_KEEPER_PID 2>/dev/null || true

log "Minecraft server process exited with code: $EXIT_CODE"
exit $EXIT_CODE