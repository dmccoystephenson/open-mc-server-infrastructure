# Minecraft Wrapper Service

A Spring Boot service that manages the Minecraft server lifecycle, providing graceful shutdown capabilities and REST API endpoints for server management.

## Features

- **Server Lifecycle Management**: Automatically starts and manages the Minecraft server process
- **Graceful Shutdown**: Warns players with countdown messages before shutting down
- **Alert Integration**: Sends alerts to the alert-manager service for server events (start, stop, crash)
- **REST API**: Exposes endpoints for server status, command execution, and message sending
- **Unit Tests**: Comprehensive test coverage for all service components

## REST API Endpoints

### Server Management

- `GET /api/server/status` - Get current server status
- `POST /api/server/command` - Send a command to the Minecraft server
- `POST /api/server/shutdown` - Initiate graceful server shutdown

### Messaging

- `POST /api/messages` - Send a message to players

Example message request:
```json
{
  "text": "Server maintenance in 5 minutes",
  "destination": "MINECRAFT"
}
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

## Docker

Build the Docker image:
```bash
./gradlew build
docker build -t minecraft-wrapper .
```

Run the container:
```bash
docker run -p 8092:8092 \
  -v /path/to/mcserver:/mcserver \
  -e ALERT_MANAGER_URL=http://alert-manager:8090/api/alerts \
  minecraft-wrapper
```
