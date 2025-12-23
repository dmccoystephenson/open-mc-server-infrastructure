# Minecraft Wrapper Service

A Spring Boot service that manages the Minecraft server lifecycle, providing graceful shutdown capabilities and REST API endpoints for server management.

## Overview

This module replaces the original `minecraft-wrapper.sh` bash script with a testable, maintainable Spring Boot application. It is integrated into the main Minecraft server container and provides:

- **Testable Logic**: All wrapper functionality is covered by unit tests (15 tests)
- **REST API**: Exposes endpoints for server management and messaging
- **Service Integration**: Other services can interact with the wrapper via HTTP

## Features

- **Server Lifecycle Management**: Automatically starts and manages the Minecraft server process
- **Graceful Shutdown**: Warns players with countdown messages before shutting down
- **Alert Integration**: Sends alerts to the alert-manager service for server events (start, stop, crash)
- **REST API**: Exposes endpoints for server status, command execution, and message sending
- **Unit Tests**: Comprehensive test coverage for all service components (15 tests)

## Deployment

The Spring Boot wrapper is built as part of the main Minecraft server Docker image and runs inside the `mcserver` container. It starts automatically when the container starts and manages the Minecraft server process.

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

## Integration with Infrastructure

The wrapper service is built and deployed as part of the main Minecraft server container. It:

1. **Integrates with Alert Manager**: Sends lifecycle alerts (start, stop, crash) via REST API
2. **Manages Minecraft Server**: Controls the server process and sends commands via FIFO
3. **Provides REST API**: Exposes endpoints for external management on port 8092
4. **Runs inside mcserver container**: No separate container needed

## Advantages of Spring Boot Implementation

The Spring Boot wrapper replaces the previous bash script with these benefits:

- ✅ **Unit tested**: 15 tests covering all core logic
- ✅ **REST API**: Remote management capabilities
- ✅ **Better error handling**: Structured logging and exception handling
- ✅ **Maintainable**: Easier to extend and modify
- ✅ **Type-safe**: Configuration via Spring properties
- ✅ **Spring Boot ecosystem**: Actuator, health checks, metrics
