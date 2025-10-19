# Web Application

This is a Spring Boot web application that provides a dashboard and admin interface for the Minecraft server.

## Features

- Server status display
- Real-time player list via RCON
- Admin console for sending server commands
- Links to external services (Dynmap, BlueMap)

## Building

To build the application:

```bash
./gradlew build
```

The JAR file will be created in `build/libs/`.

## Docker Build

The application is designed to be built before Docker image creation:

```bash
# Build the application
./gradlew build

# Build the Docker image
docker build -t private-mc-server-webapp .
```

Or use the provided docker-compose which handles this automatically.

## Configuration

The application is configured via environment variables:

- `MC_HOST`: Minecraft server hostname (default: `mcserver`)
- `MC_RCON_PORT`: RCON port (default: `25575`)
- `MC_RCON_PASSWORD`: RCON password (default: `minecraft`)
- `MC_MOTD`: Server MOTD
- `MC_MAX_PLAYERS`: Maximum players
- `DYNMAP_URL`: Optional Dynmap URL
- `BLUEMAP_URL`: Optional BlueMap URL

## Development

Run the application locally:

```bash
# Set environment variables
export MC_HOST=localhost
export MC_RCON_PORT=25575
export MC_RCON_PASSWORD=minecraft

# Run the application
./gradlew bootRun
```

The application will be available at `http://localhost:8080`.
