# Web Application

This is a Spring Boot web application that provides a dashboard and admin interface for the Minecraft server.

## Features

- Server status display
- Real-time player list via RCON
- Admin console for sending server commands (requires authentication)
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
docker build -t open-mc-server-webapp .
```

Or use the provided docker-compose which handles this automatically.

## Configuration

The application is configured via environment variables:

- `MC_HOST`: Minecraft server hostname (default: `minecraft-wrapper` — the wrapper service, not the raw mcserver)
- `MC_RCON_PORT`: RCON port (default: `25575`)
- `MC_RCON_PASSWORD`: RCON password (default: `minecraft`). When running via Compose, this is sourced from the top-level `RCON_PASSWORD` in `.env`; see `compose.yml`.
- `MC_MOTD`: Server MOTD
- `MC_MAX_PLAYERS`: Maximum players
- `ADMIN_USERNAME`: Username for admin console (default: `admin`)
- `ADMIN_PASSWORD`: Password for admin console (default: `admin`)
- `DYNMAP_URL`: Optional Dynmap URL
- `BLUEMAP_URL`: Optional BlueMap URL
- `ACCORDION_CHAT_URL`: Optional Accordion Chat URL (shown as a "Chat" link on the dashboard)

**Security Note**: Change the admin username and password from defaults in production.

## Development

Run the application locally:

```bash
# Set environment variables
# (Point MC_HOST at wherever the minecraft-wrapper REST API is reachable;
# use localhost when running both locally outside Docker.)
export MC_HOST=localhost
export MC_RCON_PORT=25575
export MC_RCON_PASSWORD=minecraft
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=admin

# Run the application
./gradlew bootRun
```

The application will be available at `http://localhost:8080`.
