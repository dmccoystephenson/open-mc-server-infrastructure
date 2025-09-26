#!/bin/bash
# Minecraft Server Wrapper - Handles graceful shutdown for plugin data preservation
set -euo pipefail

# Function: Log with timestamp
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WRAPPER] $1"
}

# Variables
SERVER_JAR="$1"
SERVER_DIR="$2" 
JAVA_OPTS="$3"
PID=""
PIPE_KEEPER_PID=""
INPUT_PIPE="$SERVER_DIR/server_input"

# Function: Graceful shutdown
graceful_shutdown() {
    log "Received shutdown signal, initiating graceful server stop..."
    
    # Clean up pipe keeper first
    if [ -n "$PIPE_KEEPER_PID" ] && kill -0 "$PIPE_KEEPER_PID" 2>/dev/null; then
        kill $PIPE_KEEPER_PID 2>/dev/null || true
    fi
    
    if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
        log "Sending 'stop' command to Minecraft server..."
        
        # Send stop command through named pipe
        if [ -p "$INPUT_PIPE" ]; then
            echo "stop" > "$INPUT_PIPE" || {
                log "Failed to send stop command via pipe, sending SIGTERM..."
                kill -TERM "$PID"
            }
        else
            log "Input pipe not available, sending SIGTERM..."
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
    
    # Clean up named pipe if it exists
    [ -p "$INPUT_PIPE" ] && rm -f "$INPUT_PIPE"
    
    exit 0
}

# Function: Cleanup on exit
cleanup() {
    # Clean up pipe keeper if it exists
    [ -n "${PIPE_KEEPER_PID:-}" ] && kill $PIPE_KEEPER_PID 2>/dev/null || true
    # Clean up named pipe
    [ -p "$INPUT_PIPE" ] && rm -f "$INPUT_PIPE"
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

# Create named pipe for server input (remove if exists)
[ -p "$INPUT_PIPE" ] && rm -f "$INPUT_PIPE"
mkfifo "$INPUT_PIPE"

# Keep the pipe open by running a background process that feeds it
# This prevents the server from blocking on stdin
{
    # Keep pipe open and forward any commands
    while true; do
        sleep 1
    done
} > "$INPUT_PIPE" &
PIPE_KEEPER_PID=$!

# Start server with input from named pipe
log "Starting server with input pipe..."
java $JAVA_OPTS -jar "$SERVER_JAR" nogui < "$INPUT_PIPE" &
PID=$!

log "Minecraft server started with PID: $PID"

# Wait for the server process to finish, but check for signals periodically
log "Waiting for server process..."
while kill -0 "$PID" 2>/dev/null; do
    sleep 1
done

# Get exit code after server finishes
wait "$PID" 2>/dev/null
EXIT_CODE=$?

# Clean up pipe keeper
kill $PIPE_KEEPER_PID 2>/dev/null || true

log "Minecraft server process exited with code: $EXIT_CODE"
exit $EXIT_CODE