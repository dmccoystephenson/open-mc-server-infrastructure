#!/bin/bash
# Minecraft Server Overload Monitor - Sends email alerts when server is overloaded
set -euo pipefail

# Function: Log with timestamp
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [MONITOR] $1"
}

# Variables
SERVER_DIR="$1"
ALERT_EMAIL="${ALERT_EMAIL:-}"
LOG_FILE="$SERVER_DIR/logs/latest.log"
ALERT_COOLDOWN=300  # 5 minutes cooldown between alerts
LAST_ALERT_FILE="/tmp/last_overload_alert"

# Function: Send email alert
send_alert() {
    local message="$1"
    
    if [ -z "$ALERT_EMAIL" ]; then
        log "ALERT_EMAIL not configured, skipping email notification"
        return
    fi
    
    # Check cooldown to prevent spam
    if [ -f "$LAST_ALERT_FILE" ]; then
        local last_alert_time
        last_alert_time=$(cat "$LAST_ALERT_FILE")
        local current_time
        current_time=$(date +%s)
        local time_diff=$((current_time - last_alert_time))
        
        if [ $time_diff -lt $ALERT_COOLDOWN ]; then
            log "Alert cooldown active, skipping notification (${time_diff}s since last alert)"
            return
        fi
    fi
    
    log "Sending email alert to $ALERT_EMAIL"
    
    local subject="Minecraft Server Overload Alert"
    local body
    body="Your Minecraft server is experiencing performance issues:

$message

Timestamp: $(date '+%Y-%m-%d %H:%M:%S')
Server: $(hostname)

This alert will not repeat for the next $ALERT_COOLDOWN seconds to prevent spam.

Please check your server resources and consider:
- Reducing loaded chunks/entities
- Increasing allocated memory
- Upgrading server hardware
- Checking for problematic plugins"
    
    # Try to send email using available methods
    if command -v mail &> /dev/null; then
        echo "$body" | mail -s "$subject" "$ALERT_EMAIL" 2>/dev/null && {
            log "Alert sent successfully via mail command"
            date +%s > "$LAST_ALERT_FILE"
            return
        }
    fi
    
    if command -v sendmail &> /dev/null; then
        {
            echo "To: $ALERT_EMAIL"
            echo "Subject: $subject"
            echo ""
            echo "$body"
        } | sendmail -t 2>/dev/null && {
            log "Alert sent successfully via sendmail"
            date +%s > "$LAST_ALERT_FILE"
            return
        }
    fi
    
    log "WARNING: No email client found (mail or sendmail). Cannot send alert."
    log "Please install mailutils or configure SMTP settings."
}

# Function: Monitor log file for overload messages
monitor_logs() {
    log "Starting overload monitoring for $LOG_FILE"
    
    # Wait for log file to be created
    local wait_time=0
    while [ ! -f "$LOG_FILE" ] && [ $wait_time -lt 300 ]; do
        log "Waiting for log file to be created..."
        sleep 5
        wait_time=$((wait_time + 5))
    done
    
    if [ ! -f "$LOG_FILE" ]; then
        log "ERROR: Log file not found after 5 minutes: $LOG_FILE"
        return 1
    fi
    
    log "Log file found, monitoring for overload messages..."
    
    # Monitor the log file for overload messages
    tail -F "$LOG_FILE" 2>/dev/null | while read -r line; do
        # Check for the "Can't keep up" message
        if echo "$line" | grep -q "Can't keep up"; then
            log "OVERLOAD DETECTED: $line"
            send_alert "$line"
        fi
    done
}

# Main execution
log "Overload monitor starting..."
log "Server directory: $SERVER_DIR"
log "Alert email: ${ALERT_EMAIL:-<not configured>}"

# Start monitoring
monitor_logs &
MONITOR_PID=$!

log "Monitor started with PID: $MONITOR_PID"

# Keep the script running and handle cleanup
trap 'log "Monitor stopping..."; kill $MONITOR_PID 2>/dev/null || true; exit 0' SIGTERM SIGINT

wait $MONITOR_PID
