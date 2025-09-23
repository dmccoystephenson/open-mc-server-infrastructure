# Private Minecraft Server

[![CI Pipeline](https://github.com/dmccoystephenson/private-mc-server/workflows/CI%20Pipeline/badge.svg?branch=main)](https://github.com/dmccoystephenson/private-mc-server/actions)

A Docker-based private Minecraft server running the latest version of Minecraft (1.21.8) with Spigot for enhanced plugin support and performance.

## Features

- **Latest Minecraft Version**: Running Minecraft 1.21.8 with Spigot
- **Docker Containerized**: Easy deployment and management
- **Configurable**: Environment-based configuration
- **Persistent Data**: Server data persists across container restarts
- **Easy Management**: Simple scripts for starting and stopping the server

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/)
- [Docker Compose](https://docs.docker.com/compose/install/)
- [Git](https://git-scm.com/downloads)

## Quick Start

1. **Clone the repository**
   ```bash
   git clone <your-repo-url>
   cd private-mc-server
   ```

2. **Configure the server**
   ```bash
   cp sample.env .env
   # Edit .env with your settings (see Configuration section)
   ```

3. **Start the server**
   ```bash
   chmod +x up.sh down.sh
   ./up.sh
   ```
   
   **Note**: The first build will take 10-15 minutes as it downloads and compiles Spigot from source.

4. **Connect to your server**
   - Server address: `localhost:25565` (or your server's IP)
   - The server will take a few minutes to build on first run

## Configuration

Copy `sample.env` to `.env` and modify the following settings:

### Essential Settings
- `OPERATOR_UUID`: Your Minecraft player UUID (get from [mcuuid.net](https://mcuuid.net/))
- `OPERATOR_NAME`: Your Minecraft username
- `SERVER_MOTD`: Message displayed in the server list
- `MAX_PLAYERS`: Maximum number of players allowed

**Note**: If `OPERATOR_UUID` and `OPERATOR_NAME` are not properly configured, the server will still start but you'll need to manually add operators using the `op <username>` command in the server console.

### Server Settings
- `DIFFICULTY`: Server difficulty (peaceful, easy, normal, hard)
- `GAMEMODE`: Default game mode (survival, creative, adventure, spectator)
- `PVP_ENABLED`: Enable/disable player vs player combat
- `ONLINE_MODE`: Enable Mojang authentication (set to false for offline/cracked servers)

## Management

### Starting the Server
```bash
./up.sh
```
or
```bash
docker compose up -d --build
```

### Stopping the Server
```bash
./down.sh
```
or
```bash
docker compose down
```

### Server Console Access
```bash
docker exec -it private-mc-server /bin/bash
```

### Viewing Server Logs
```bash
docker logs -f private-mc-server
```

### Server Administration
Once connected to the server console, you can run server commands:
```bash
# Inside the container
docker exec -it private-mc-server java -jar spigot-1.21.8.jar
# Then use standard Minecraft server commands
```

## File Management

### Backup Server Data
```bash
docker cp private-mc-server:/mcserver ./backup/
```

### Restore Server Data
```bash
docker cp ./backup/ private-mc-server:/mcserver
docker compose restart
```

### Deposit Box
The `deposit-box` directory is shared between your host system and the container at `/deposit-box`. Use it to transfer files to/from the server.

## Updating

### Update Minecraft Version
1. Edit `sample.env` and `.env` to change `MINECRAFT_VERSION`
2. Set `OVERWRITE_EXISTING_SERVER=true` in `.env` (⚠️ This will reset your world!)
3. Restart the server: `./down.sh && ./up.sh`

### Update Container
```bash
./down.sh
docker compose build --no-cache
./up.sh
```

## Troubleshooting

### Server Won't Start
- Check Docker logs: `docker logs private-mc-server`
- Ensure all required environment variables are set
- Verify Docker and Docker Compose are installed

### Can't Connect to Server
- Ensure port 25565 is open/forwarded
- Check if `ONLINE_MODE` setting matches your client type
- Verify the server is running: `docker ps`

### Performance Issues
- Adjust memory allocation in `resources/post-create.sh` (modify `-Xmx2G -Xms1G`)
- Monitor system resources: `docker stats private-mc-server`

## Security Notes

- Change default operator settings in `.env`
- Consider setting `ONLINE_MODE=true` for authentication
- Don't expose the server publicly without proper security measures
- Regularly backup your world data

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Development

### CI/CD Pipeline

This repository includes a comprehensive CI pipeline that automatically validates:

- **Shell Script Validation**: Syntax checking and ShellCheck linting for all bash scripts
- **Docker Configuration**: Validates Dockerfile and Docker Compose configurations
- **Environment Configuration**: Ensures all required environment variables are properly defined
- **Security Scanning**: Trivy security scanning for vulnerabilities
- **Documentation**: Validates README and checks for broken links
- **Integration Testing**: End-to-end validation of the complete setup

### Running Local CI Checks

Before submitting changes, you can run the same validation checks locally:

```bash
./scripts/ci-local.sh
```

This will run basic validation checks that mirror the CI pipeline to catch issues early.

### CI Pipeline Status

The CI pipeline runs on:
- Every push to `main` and `develop` branches
- Every pull request to `main`

Check the [Actions tab](https://github.com/dmccoystephenson/private-mc-server/actions) for detailed CI results and logs.

## Contributing

Feel free to submit issues and enhancement requests!