# Alert Manager

An alert notification system for the Minecraft server infrastructure. This Spring Boot application runs as a separate container and handles sending notifications to various destinations, starting with Discord. Other modules (backup-manager, webapp, etc.) can use this module to send alerts to server administrators and community members.

## Features

- **Discord Notifications**: Send alerts to Discord channels via webhooks
- **Multiple Alert Levels**: Support for INFO, WARNING, ERROR, and CRITICAL alert levels
- **REST API**: Easy integration with other modules via HTTP endpoints
- **Containerized**: Runs in its own Docker container for isolation
- **Extensible**: Designed to support additional notification destinations (Slack, Email, SMS, etc.)

## Configuration

### Environment Variables

The following environment variables can be configured in `.env`:

- `ALERT_CONTAINER_NAME`: Container name (default: `open-mc-alert-manager`)
- `ALERT_PORT`: Port for the alert manager API (default: `8090`)
- `DISCORD_WEBHOOK_URL`: Discord webhook URL for sending notifications
- `DISCORD_ENABLED`: Enable/disable Discord notifications (default: `false`)
- `MINECRAFT_RCON_HOST`: Minecraft server hostname for RCON (default: `mcserver`)
- `MINECRAFT_RCON_PORT`: Minecraft server RCON port (default: `25575`)
- `MINECRAFT_RCON_PASSWORD`: Password for Minecraft RCON (default: uses `RCON_PASSWORD` from compose.yml)
- `MINECRAFT_RCON_ENABLED`: Enable/disable Minecraft message sending (default: `true`)

### Discord Webhook Setup

To enable Discord notifications:

1. Create a webhook in your Discord server:
   - Go to Server Settings → Integrations → Webhooks
   - Click "New Webhook"
   - Choose a channel and copy the webhook URL
   
2. Add the webhook URL to your `.env` file with `DISCORD_WEBHOOK_URL` and set `DISCORD_ENABLED=true`

3. Restart the infrastructure

### Minecraft RCON Setup

The alert manager can send messages directly to the Minecraft server using RCON. This is enabled by default and uses the same RCON password configured for the Minecraft server. The minecraft-wrapper service uses this functionality to send shutdown warnings to players.

## Alert Levels

The alert manager supports four severity levels:

- **INFO**: Informational messages (blue in Discord)
- **WARNING**: Warning messages that require attention (yellow in Discord)
- **ERROR**: Error messages for failures (red in Discord)
- **CRITICAL**: Critical issues requiring immediate attention (dark red in Discord)

## API Endpoints

### Send Alert

**POST** `/api/alerts`

Send an alert notification to one or more destinations.

Request body:
```json
{
  "title": "Alert Title",
  "message": "Alert message content",
  "level": "INFO",
  "source": "module-name",
  "destinations": ["DISCORD", "MINECRAFT"]
}
```

Fields:
- `title` (optional): Title of the alert
- `message` (required): Message content
- `level` (required): Alert severity level (INFO, WARNING, ERROR, CRITICAL)
- `source` (required): Source module generating the alert
- `destinations` (optional): Array of destination enums. If not specified, sends to all configured destinations.

Supported destinations:
- `DISCORD` - Sends alert to Discord via webhook
- `MINECRAFT` - Sends message to Minecraft server via RCON

Response: Success message confirming alert was sent.

### Health Check

**GET** `/api/alerts/health`

Check if the alert manager is running and returns a status message.

## Usage

The alert-manager provides a simple REST API that can be called from any module using standard HTTP POST requests. Other modules in the infrastructure (backup-manager, webapp, etc.) can send alerts by making HTTP POST requests to the `/api/alerts` endpoint with JSON payloads containing the alert details.

## Building

Build the alert-manager application using the provided build script or Gradle directly.

## Testing

Run tests using Gradle's test task.

### Integration Testing

An example integration script is provided to test the alert-manager from the host machine. The script demonstrates how to send alerts from other modules and verifies that the alert-manager API is working correctly.

## Running

The alert-manager is automatically started with the rest of the infrastructure using the up.sh script.

### Viewing Logs

View alert-manager logs using Docker's log command with the container name (default: open-mc-alert-manager).

## Integration with Other Modules

The alert-manager is designed to be used by other modules in the infrastructure. Modules can integrate by making HTTP POST requests to the alert-manager API endpoint with appropriate alert details including title, message, level, and source identification.

## Future Enhancements

The alert-manager is designed to be extensible. Future versions may include:

- **Slack Integration**: Send alerts to Slack channels
- **Email Notifications**: Send alerts via email
- **SMS Alerts**: Send critical alerts via SMS
- **Alert Filtering**: Configure which alert levels to send to each destination
- **Alert Scheduling**: Quiet hours for non-critical alerts
- **Alert Grouping**: Group similar alerts to reduce noise
- **Webhook Retries**: Retry failed webhook deliveries

## Architecture

```
┌─────────────────┐
│  Backup Manager │──┐
└─────────────────┘  │
                     │
┌─────────────────┐  │    ┌──────────────────┐
│   Web App       │──┼───→│  Alert Manager   │
└─────────────────┘  │    │                  │
                     │    │  - REST API      │
┌─────────────────┐  │    │  - Discord       │
│  Other Modules  │──┘    │  - (Future...)   │
└─────────────────┘       └──────────────────┘
                                    │
                                    ↓
                          ┌──────────────────┐
                          │    Discord       │
                          │    Server        │
                          └──────────────────┘
```

## Security Notes

- Store webhook URLs securely in environment variables, never in code
- Consider using a reverse proxy with authentication for production deployments
- Monitor alert volume to prevent spam or abuse
- Regularly rotate webhook URLs if compromised

## Troubleshooting

### Alerts Not Being Sent

1. Check container logs: `docker logs open-mc-alert-manager`
2. Verify Discord is enabled: `DISCORD_ENABLED=true` in `.env`
3. Verify webhook URL is correct in `.env`
4. Ensure the container is running: `docker ps | grep alert-manager`

### Discord Webhook Fails

1. Verify the webhook URL is still valid in Discord
2. Check Discord server permissions
3. Ensure the webhook hasn't been deleted
4. Check for rate limiting in the logs

### Can't Connect to Alert Manager API

1. Verify the container is running and healthy
2. Check the port mapping in compose.yml
3. Test the health endpoint using an HTTP client
4. Check network connectivity between containers
