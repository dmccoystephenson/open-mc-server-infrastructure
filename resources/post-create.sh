#!/bin/bash
set -euo pipefail

SERVER_DIR="/mcserver"
BUILD_DIR="/mcserver-build"

# Function: Log a message with the [SERVER-SETUP] prefix
log() {
    local message="$1"
    echo "[SERVER-SETUP] $message"
}

# Function: Send alert to alert-manager
send_alert() {
    local title="$1"
    local message="$2"
    local level="${3:-INFO}"
    local source="server-setup"
    local alert_toggle="${4:-}"
    
    # Check if this type of alert is enabled (if toggle variable is provided)
    if [ -n "$alert_toggle" ]; then
        local toggle_value="${!alert_toggle:-true}"
        if [ "$toggle_value" != "true" ]; then
            return 0
        fi
    fi
    
    # Determine the alert manager URL based on environment
    local alert_url
    if [ -n "${ALERT_MANAGER_URL:-}" ]; then
        alert_url="$ALERT_MANAGER_URL"
    elif [ -f /.dockerenv ] || grep -q docker /proc/1/cgroup 2>/dev/null; then
        alert_url="http://alert-manager:8090/api/alerts"
    else
        alert_url="http://localhost:8090/api/alerts"
    fi
    
    # Try to send alert, but don't fail if it doesn't work
    if command -v curl >/dev/null 2>&1; then
        curl -X POST "$alert_url" \
          -H "Content-Type: application/json" \
          --max-time 5 \
          --connect-timeout 5 \
          -d "{\"title\":\"$title\",\"message\":\"$message\",\"level\":\"$level\",\"source\":\"$source\"}" \
          >/dev/null 2>&1 || true
    fi
}

# Function: Validate required environment variables
validate_environment() {
    local warnings=false
    local warning_messages=""
    
    if [ "$OPERATOR_UUID" = "YOUR_UUID_HERE" ] || [ -z "$OPERATOR_UUID" ]; then
        log "WARNING: OPERATOR_UUID is not set properly. Consider setting it to your actual UUID from https://mcuuid.net/"
        log "Server will continue with default operator configuration."
        warnings=true
        warning_messages="OPERATOR_UUID not configured"
    fi
    
    if [ "$OPERATOR_NAME" = "YOUR_USERNAME_HERE" ] || [ -z "$OPERATOR_NAME" ]; then
        log "WARNING: OPERATOR_NAME is not set properly. Consider setting it to your actual Minecraft username."
        log "Server will continue with default operator configuration."
        warnings=true
        if [ -n "$warning_messages" ]; then
            warning_messages="$warning_messages, OPERATOR_NAME not configured"
        else
            warning_messages="OPERATOR_NAME not configured"
        fi
    fi
    
    if [ "$warnings" = false ]; then
        log "Environment validation passed."
    else
        log "Server starting with configuration warnings - please check your .env file."
        send_alert "Server Configuration Warning" "Server started with configuration issues: $warning_messages" "WARNING" "ALERTS_CONFIG_WARNING"
    fi
}

# Function: Setup server
setup_server() {
    if [ -z "$(ls -A "$SERVER_DIR")" ] || [ "$OVERWRITE_EXISTING_SERVER" = "true" ]; then
        log "Setting up new server..."
        rm -rf "${SERVER_DIR:?}"/*
        cp "$BUILD_DIR"/spigot-"${MINECRAFT_VERSION}".jar "$SERVER_DIR"/spigot-"${MINECRAFT_VERSION}".jar
        mkdir -p "$SERVER_DIR"/plugins
    else
        log "Server is already set up."
        
        # Check if we need to update the server JAR for a version upgrade
        local new_jar="$BUILD_DIR/spigot-${MINECRAFT_VERSION}.jar"
        local expected_jar="$SERVER_DIR/spigot-${MINECRAFT_VERSION}.jar"
        
        # Check if the expected JAR for this version exists
        if [ ! -f "$expected_jar" ]; then
            # The expected JAR doesn't exist, so we need to upgrade
            log "Detected version change - updating server JAR to ${MINECRAFT_VERSION}..."
            
            # Check if there are old version JARs to remove
            if ls "$SERVER_DIR"/spigot-*.jar >/dev/null 2>&1; then
                local old_jars
                old_jars=$(find "$SERVER_DIR" -name "spigot-*.jar" -exec basename {} \;)
                log "Removing old JAR(s): $old_jars"
                rm -f "$SERVER_DIR"/spigot-*.jar
            fi
            
            # Copy new version JAR
            cp "$new_jar" "$expected_jar"
            log "Server JAR updated successfully to version ${MINECRAFT_VERSION}."
        else
            log "Server JAR is up to date (version ${MINECRAFT_VERSION})."
        fi
    fi
}

# Function: Setup ops.json file
setup_ops_file() {
    # Check if ops.json already exists - if so, preserve it to maintain runtime op changes
    if [ -f "$SERVER_DIR/ops.json" ]; then
        log "ops.json already exists - preserving existing operator configuration."
        return
    fi
    
    # Only create ops.json if we have valid operator information
    if [ "$OPERATOR_UUID" != "YOUR_UUID_HERE" ] && [ -n "$OPERATOR_UUID" ] && [ "$OPERATOR_NAME" != "YOUR_USERNAME_HERE" ] && [ -n "$OPERATOR_NAME" ]; then
        log "Creating ops.json file with operator: ${OPERATOR_NAME}"
        cat <<EOF > "$SERVER_DIR"/ops.json
[
  {
    "uuid": "${OPERATOR_UUID}",
    "name": "${OPERATOR_NAME}",
    "level": ${OPERATOR_LEVEL},
    "bypassesPlayerLimit": false
  }
]
EOF
    else
        log "Skipping ops.json creation - operator information not properly configured."
        log "You can add operators manually using the 'op <username>' command in the server console."
    fi
}

# Function: Accept EULA
accept_eula() {
    log "Accepting Minecraft EULA..."
    echo "eula=true" > "$SERVER_DIR"/eula.txt
}

# Function: Create server properties
create_server_properties() {
    log "Creating server.properties file..."
    cat <<EOF > "$SERVER_DIR"/server.properties
#Minecraft server properties
enable-jmx-monitoring=false
rcon.port=25575
level-seed=
gamemode=${GAMEMODE}
enable-command-block=false
enable-query=false
generator-settings={}
enforce-secure-profile=true
level-name=world
motd=${SERVER_MOTD}
query.port=25565
pvp=${PVP_ENABLED}
generate-structures=true
max-chained-neighbor-updates=1000000
difficulty=${DIFFICULTY}
network-compression-threshold=256
max-tick-time=60000
require-resource-pack=false
use-native-transport=true
max-players=${MAX_PLAYERS}
online-mode=${ONLINE_MODE}
enable-status=true
allow-flight=false
initial-disabled-packs=
broadcast-rcon-to-ops=true
view-distance=10
server-ip=
resource-pack-prompt=
allow-nether=true
server-port=25565
enable-rcon=true
sync-chunk-writes=true
op-permission-level=4
prevent-proxy-connections=false
hide-online-players=false
resource-pack=
entity-broadcast-range-percentage=100
simulation-distance=10
rcon.password=${RCON_PASSWORD}
player-idle-timeout=0
debug=false
force-gamemode=false
rate-limit=0
hardcore=false
white-list=false
broadcast-console-to-ops=true
spawn-npcs=true
spawn-animals=true
function-permission-level=2
initial-enabled-packs=vanilla
level-type=minecraft\:normal
text-filtering-config=
spawn-monsters=true
enforce-whitelist=false
spawn-protection=16
resource-pack-sha1=
max-world-size=29999984
EOF
}

# Function: Start server
start_server() {
    log "Starting server with Spring Boot wrapper..."
    exec java -jar /app/minecraft-wrapper.jar \
        --minecraft.server.jar="spigot-${MINECRAFT_VERSION}.jar" \
        --minecraft.server.directory="$SERVER_DIR" \
        --minecraft.java.opts="${JAVA_OPTS:--Xmx2G -Xms1G}" \
        --minecraft.auto.start=true
}

# Main Process
log "Running server setup script..."
validate_environment
setup_server
setup_ops_file
accept_eula
create_server_properties

# Start Server
start_server