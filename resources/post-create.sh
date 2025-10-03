#!/bin/bash
set -euo pipefail

SERVER_DIR="/mcserver"
BUILD_DIR="/mcserver-build"

# Function: Log a message with the [SERVER-SETUP] prefix
log() {
    local message="$1"
    echo "[SERVER-SETUP] $message"
}

# Function: Validate required environment variables
validate_environment() {
    local warnings=false
    
    if [ "$OPERATOR_UUID" = "YOUR_UUID_HERE" ] || [ -z "$OPERATOR_UUID" ]; then
        log "WARNING: OPERATOR_UUID is not set properly. Consider setting it to your actual UUID from https://mcuuid.net/"
        log "Server will continue with default operator configuration."
        warnings=true
    fi
    
    if [ "$OPERATOR_NAME" = "YOUR_USERNAME_HERE" ] || [ -z "$OPERATOR_NAME" ]; then
        log "WARNING: OPERATOR_NAME is not set properly. Consider setting it to your actual Minecraft username."
        log "Server will continue with default operator configuration."
        warnings=true
    fi
    
    if [ "$warnings" = false ]; then
        log "Environment validation passed."
    else
        log "Server starting with configuration warnings - please check your .env file."
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

# Function: Install default plugins
install_default_plugins() {
    # Check if DEFAULT_PLUGINS is defined and not empty
    if [ -z "${DEFAULT_PLUGINS:-}" ]; then
        log "No default plugins configured."
        return 0
    fi
    
    log "Installing default plugins..."
    
    # Ensure plugins directory exists
    mkdir -p "$SERVER_DIR"/plugins
    
    # Split the comma-separated list and download each plugin
    IFS=',' read -ra PLUGIN_URLS <<< "$DEFAULT_PLUGINS"
    local success_count=0
    local fail_count=0
    
    for url in "${PLUGIN_URLS[@]}"; do
        # Trim whitespace
        url=$(echo "$url" | xargs)
        
        # Skip empty entries
        if [ -z "$url" ]; then
            continue
        fi
        
        # Extract filename from URL
        local filename
        filename=$(basename "$url")
        
        log "Downloading plugin: $filename from $url"
        
        # Download the plugin
        if wget -q -O "$SERVER_DIR/plugins/$filename" "$url"; then
            log "Successfully downloaded: $filename"
            success_count=$((success_count + 1))
        else
            log "WARNING: Failed to download plugin from $url"
            fail_count=$((fail_count + 1))
        fi
    done
    
    if [ $success_count -gt 0 ]; then
        log "Plugin installation completed: $success_count succeeded, $fail_count failed"
    else
        log "No plugins were successfully installed."
    fi
}

# Function: Setup ops.json file
setup_ops_file() {
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
enable-rcon=false
sync-chunk-writes=true
op-permission-level=4
prevent-proxy-connections=false
hide-online-players=false
resource-pack=
entity-broadcast-range-percentage=100
simulation-distance=10
rcon.password=
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
    log "Starting server with graceful shutdown wrapper..."
    /resources/minecraft-wrapper.sh \
        "spigot-${MINECRAFT_VERSION}.jar" \
        "$SERVER_DIR" \
        "${JAVA_OPTS:--Xmx2G -Xms1G}"
}

# Main Process
log "Running server setup script..."
validate_environment
setup_server
install_default_plugins
setup_ops_file
accept_eula
create_server_properties

# Start Server
start_server