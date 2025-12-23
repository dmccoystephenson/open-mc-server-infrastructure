# Minecraft Wrapper Service

A Spring Boot service that manages the Minecraft server lifecycle, providing graceful shutdown capabilities and REST API endpoints for server management.

## Overview

This module extracts the logic from the original `minecraft-wrapper.sh` bash script into a testable, maintainable Spring Boot application. It provides:

- **Testable Logic**: All wrapper functionality is now covered by unit tests
- **REST API**: Exposes endpoints for server management and messaging
- **Service Integration**: Other services can interact with the wrapper via HTTP instead of shell scripts

## Features

- **Server Lifecycle Management**: Automatically starts and manages the Minecraft server process
- **Graceful Shutdown**: Warns players with countdown messages before shutting down
- **Alert Integration**: Sends alerts to the alert-manager service for server events (start, stop, crash)
- **REST API**: Exposes endpoints for server status, command execution, and message sending
- **Unit Tests**: Comprehensive test coverage for all service components (15 tests)

## Architecture

The wrapper service can be deployed in two ways:

### Option 1: Standalone Service (Current Implementation)
The Spring Boot service runs as a separate container that can manage its own Minecraft server instance or be used by other services via REST API.

### Option 2: Replace Bash Script (Future Enhancement)
The Spring Boot service could replace the bash wrapper script in the main Minecraft container, providing the same functionality with better testability.

## REST API Endpoints

### Server Management

- `GET /api/server/status` - Get current server status
  ```json
  {
    "running": true,
    "pid": 12345,
    "serverJar": "spigot-1.21.10.jar",
    "serverDirectory": "/mcserver"
  }
  ```

- `POST /api/server/command` - Send a command to the Minecraft server
  ```bash
  curl -X POST http://localhost:8092/api/server/command \
    -H "Content-Type: text/plain" \
    -d "say Hello, World!"
  ```

- `POST /api/server/shutdown` - Initiate graceful server shutdown
  ```bash
  curl -X POST http://localhost:8092/api/server/shutdown
  ```

### Messaging

- `POST /api/messages` - Send a message to players
  ```bash
  curl -X POST http://localhost:8092/api/messages \
    -H "Content-Type: application/json" \
    -d '{"text": "Server maintenance in 5 minutes", "destination": "MINECRAFT"}'
  ```

## Configuration

The service is configured via environment variables or `application.properties`:

```properties
# Server port
server.port=8092

# Minecraft server configuration
minecraft.server.jar=/mcserver/spigot-1.21.10.jar
minecraft.server.directory=/mcserver
minecraft.java.opts=-Xmx2G -Xms1G
minecraft.auto.start=true

# Alert manager configuration
alert.manager.url=http://alert-manager:8090/api/alerts
alerts.server.start=true
alerts.server.stop=true
alerts.server.crash=true
```

## Building

```bash
./gradlew build
```

## Running Tests

```bash
./gradlew test
```

All tests pass, providing confidence in the wrapper logic:
- AlertServiceTest: 8 tests
- MessageServiceTest: 4 tests  
- ShutdownServiceTest: 2 tests
- MinecraftWrapperApplicationTest: 1 test

## Docker Deployment

### Build the Docker image:
```bash
./gradlew build
docker build -t minecraft-wrapper .
```

### Run as standalone service:
```bash
docker run -p 8092:8092 \
  -v mcserver:/mcserver \
  -e ALERT_MANAGER_URL=http://alert-manager:8090/api/alerts \
  -e MINECRAFT_AUTO_START=true \
  minecraft-wrapper
```

## Integration with Existing Infrastructure

The wrapper service integrates with:

1. **Alert Manager**: Sends lifecycle alerts (start, stop, crash) via REST API
2. **Minecraft Server**: Manages the server process and sends commands via FIFO
3. **Other Services**: Provides REST endpoints for external management

## Migration from Bash Script

The original bash script (`resources/minecraft-wrapper.sh`) provides the same functionality as this Spring Boot service. The Spring Boot version offers several advantages:

### Advantages
- ✅ Unit tested (15 tests covering all core logic)
- ✅ REST API for remote management
- ✅ Better error handling and logging
- ✅ Easier to extend and maintain
- ✅ Type-safe configuration
- ✅ Spring Boot ecosystem integration

### Bash Script Advantages
- ✅ No Java/JVM overhead
- ✅ Simpler deployment (single file)
- ✅ Already integrated and tested in production

Both implementations are maintained for flexibility. Choose based on your needs:
- Use **bash script** for minimal overhead and proven reliability
- Use **Spring Boot** for testability, REST API access, and easier maintenance
