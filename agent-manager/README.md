# Agent Manager

A Discord-based server management agent for the Minecraft server infrastructure. This Spring Boot application runs as a separate container and provides an agentic AI interface via Discord — users send natural language messages to a designated channel, and the agent interprets the request, selects the appropriate tool, and executes server management actions via the existing `minecraft-wrapper` REST API.

## Features

- **Natural Language Commands**: Send plain English messages to manage the Minecraft server
- **Discord Bot Integration**: Bidirectional communication via a Discord bot (JDA)
- **Anthropic API**: Uses Claude to interpret user intent and select appropriate tools
- **Server Management Tools**: Start, stop, and restart the Minecraft server
- **Confirmation Flow**: Optional per-tool confirmation via Discord reactions (✅)
- **Graceful Shutdown**: Stop and restart leverage minecraft-wrapper's graceful shutdown with player countdown warnings
- **Alert Integration**: Sends alerts to `alert-manager` when tool executions occur, including the Discord user, action, and original prompt
- **Configurable**: Environment-based configuration for all credentials and behavior toggles
- **Containerized**: Runs in its own Docker container for isolation

## Architecture

```
┌──────────────┐     ┌──────────────────┐     ┌───────────────┐
│   Discord    │────→│  Agent Manager   │────→│  Anthropic    │
│   Channel    │←────│                  │←────│  API          │
└──────────────┘     │  - Discord Bot   │     └───────────────┘
                     │  - Agent Loop    │
                     │  - Tool Executor │     ┌───────────────┐
                     │  - Confirmation  │────→│  Minecraft    │
                     │  - Alert Client  │     │  Wrapper API  │
                     └────────┬─────────┘     │  (port 8092)  │
                              │               └───────────────┘
                              │               ┌───────────────┐
                              └──────────────→│  Alert Manager │
                                              │  (port 8090)  │
                                              └───────────────┘
```

### Agent Loop

1. User sends a natural language message to the configured Discord channel
2. The agent passes the message to the Anthropic API with a system prompt and tool definitions
3. If the model returns a tool call and confirmation is required, the bot prompts the user to react with ✅
4. Only the original requesting user can confirm the action
5. On confirmation (or immediately if confirmation is disabled), the agent calls the `minecraft-wrapper` REST API
6. The result is sent back to the Anthropic API for a natural language summary, which is posted to Discord
7. An alert is sent to `alert-manager` with the Discord username, action taken, and original prompt

### Alerts

When a tool execution occurs (start, stop, or restart), the agent-manager sends an alert to `alert-manager` via its REST API. Each alert includes:

- **Discord User**: The username of the player who triggered the action
- **Action**: The tool that was executed (e.g., Start Server, Stop Server, Restart Server)
- **Result**: Whether the action succeeded or failed
- **Original Prompt**: The natural language message the user sent

Alerts are sent with level `INFO` for successful executions and `WARNING` for failures. The source is set to `agent-manager` and the destination is `DISCORD`, so alerts are forwarded to the configured Discord webhook by `alert-manager`.

> **Note**: Alert sending is best-effort — if `alert-manager` is unreachable, the agent continues to function normally. The alert URL is pre-configured in Docker Compose to `http://alert-manager:8090/api/alerts`.

## Configuration

### Environment Variables

The following environment variables can be configured in `.env`:

- `AGENT_CONTAINER_NAME`: Container name (default: `open-mc-agent-manager`)
- `AGENT_PORT`: Port for the agent manager API (default: `8093`)
- `AGENT_DISCORD_BOT_TOKEN`: Discord bot token (**required**)
- `AGENT_DISCORD_CHANNEL_ID`: Discord channel ID to listen on (**required**)
- `AGENT_ANTHROPIC_API_KEY`: Anthropic API key (**required**)
- `AGENT_ENABLED`: Enable/disable the agent manager (default: `false`)
- `ALERT_MANAGER_URL`: URL for the alert-manager API (default: `http://alert-manager:8090/api/alerts`)

### Per-Tool Confirmation Toggles

- `AGENT_START_SERVER_REQUIRES_CONFIRMATION`: Require confirmation to start (default: `true`)
- `AGENT_STOP_SERVER_REQUIRES_CONFIRMATION`: Require confirmation to stop (default: `true`)
- `AGENT_RESTART_SERVER_REQUIRES_CONFIRMATION`: Require confirmation to restart (default: `true`)

### Discord Bot Setup

To enable the agent manager:

1. Create a Discord application at the [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a bot for your application and copy the bot token
3. Enable the following **Privileged Gateway Intents** under the **Bot** settings page:
   - **Message Content Intent** (required — the bot needs to read message text)
   - **Server Members Intent** (optional)
4. Invite the bot to your Discord server using the **OAuth2 → URL Generator**:
   - **Scopes**: `bot`
   - **Bot Permissions**: `Send Messages`, `Read Message History`, `Add Reactions`, `View Channels`
5. Verify the bot's role has not been denied access at the **channel level** — right-click the target channel → Edit Channel → Permissions and check for overrides
6. Copy the channel ID of the channel you want the bot to listen on (right-click channel → Copy Channel ID, with Developer Mode enabled in User Settings → Advanced)
7. Add the credentials to your `.env` file:
   ```bash
   AGENT_DISCORD_BOT_TOKEN=your-bot-token
   AGENT_DISCORD_CHANNEL_ID=your-channel-id
   AGENT_ANTHROPIC_API_KEY=your-anthropic-api-key
   AGENT_ENABLED=true
   ```
8. Restart the infrastructure with `./up.sh`

## Tools

The agent exposes three tools to the Anthropic API:

| Tool | Description | Wrapper Endpoint |
|------|-------------|-----------------|
| `start_server` | Starts the Minecraft server | `POST /api/server/start` |
| `stop_server` | Gracefully stops the server with player warnings | `POST /api/server/stop` |
| `restart_server` | Gracefully restarts the server | `POST /api/server/restart` |

All tools call the `minecraft-wrapper` REST API (port 8092), consistent with how other modules interact with it. The `stop_server` and `restart_server` tools leverage the graceful shutdown behavior already implemented in `minecraft-wrapper` — players receive countdown warnings at 30, 20, 10, and 5 seconds before the server stops.

## Building

Build the agent-manager application:

```bash
cd agent-manager
./gradlew clean build
```

## Testing

Run tests:

```bash
./gradlew test
```

## Running

The agent-manager is automatically started with the rest of the infrastructure:

```bash
cd ..
./up.sh
```

### Viewing Logs

```bash
docker logs -f open-mc-agent-manager
```

Or use your custom container name:

```bash
docker logs -f ${AGENT_CONTAINER_NAME}
```

## Dynamic Log Level Management

The agent-manager exposes Spring Boot Actuator's `/loggers` endpoint, which allows you to view and change log levels at runtime without restarting the container. This endpoint is bound to `127.0.0.1` (localhost only), so it is not accessible from outside the container — use `docker exec` to access it.

### View Current Log Levels

View all configured loggers:

```bash
curl -s http://localhost:8094/actuator/loggers | jq .
```

View the log level for the agent-manager package:

```bash
curl -s http://localhost:8094/actuator/loggers/com.openmc.agentmanager | jq .
```

Example response:
```json
{
  "configuredLevel": "INFO",
  "effectiveLevel": "INFO"
}
```

### Change Log Level at Runtime

Enable DEBUG logging for detailed message flow, API calls, and confirmation tracking:

```bash
curl -X POST http://localhost:8094/actuator/loggers/com.openmc.agentmanager \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel": "DEBUG"}'
```

Reset back to INFO:

```bash
curl -X POST http://localhost:8094/actuator/loggers/com.openmc.agentmanager \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel": "INFO"}'
```

You can also target specific services for more focused debugging:

```bash
# Debug only the Discord bot service
curl -X POST http://localhost:8094/actuator/loggers/com.openmc.agentmanager.service.DiscordBotService \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel": "DEBUG"}'

# Debug only the Anthropic API client
curl -X POST http://localhost:8094/actuator/loggers/com.openmc.agentmanager.service.AnthropicService \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel": "DEBUG"}'

# Debug only tool execution
curl -X POST http://localhost:8094/actuator/loggers/com.openmc.agentmanager.service.ToolExecutionService \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel": "DEBUG"}'
```

### From Inside the Docker Network

Since the management server is bound to `127.0.0.1` inside the container, use `docker exec`:

```bash
# View log level
docker exec open-mc-agent-manager curl -s http://localhost:8094/actuator/loggers/com.openmc.agentmanager

# Enable DEBUG
docker exec open-mc-agent-manager curl -X POST http://localhost:8094/actuator/loggers/com.openmc.agentmanager \
  -H 'Content-Type: application/json' \
  -d '{"configuredLevel": "DEBUG"}'
```

### What DEBUG Logging Shows

At DEBUG level, the agent-manager logs additional detail at each step:

| Area | Debug Details |
|------|--------------|
| **Discord Bot** | Ignored messages (bot/wrong channel/blank), executor thread dispatch, reaction handling, RestAction success/failure callbacks |
| **Agent Loop** | Anthropic response metadata (stop_reason, content block count), tool call IDs, confirmation flow decisions |
| **Anthropic API** | Request parameters (model, tool count), API URL, response status codes |
| **Tool Execution** | Tool execution IDs, wrapper request URLs |
| **Confirmations** | Expired entry details during cleanup, pending confirmation counts |

## Security Notes

- Store the Discord bot token, Anthropic API key, and channel ID securely in environment variables, never in code
- The agent is disabled by default (`AGENT_ENABLED=false`) — it must be explicitly enabled
- The system prompt narrowly constrains the agent to server management actions only
- Confirmation is required by default for all destructive actions
- Only the user who requested an action can confirm it via reaction
- Pending confirmations expire after 5 minutes to prevent stale actions
- The Discord bot only listens in the configured channel
- The Actuator management endpoints (`/loggers`, `/health`) run on a separate port (8094) and are bound to `127.0.0.1` — only accessible from inside the container

## Troubleshooting

### `ErrorResponseException: 50001: Missing Access`

This error means the bot lacks permission to send messages in the configured channel. To fix:

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) and select your application
2. Go to **OAuth2 → URL Generator**
3. Under **Scopes**, select `bot`
4. Under **Bot Permissions**, enable at minimum:
   - **Send Messages**
   - **Read Message History**
   - **Add Reactions**
   - **View Channels**
5. Copy the generated URL and use it to re-invite the bot to your server
6. Additionally, check the **channel-level permissions** in Discord:
   - Right-click the target channel → **Edit Channel** → **Permissions**
   - Ensure the bot's role is not denied **Send Messages** or **View Channel** by a channel override
7. Restart the agent-manager: `docker compose restart agent-manager`

> **Note**: Server-level role permissions can be overridden at the channel level. Even if the bot's role has `Send Messages` globally, a channel-specific override can deny it.

### Bot Not Responding

1. Check container logs: `docker logs open-mc-agent-manager`
2. Verify `AGENT_ENABLED=true` in `.env`
3. Verify the bot token and channel ID are correctly configured
4. Ensure the bot has been invited with the required permissions (see [Missing Access](#errorresponseexception-50001-missing-access) above)
5. Ensure the **Message Content Intent** is enabled in the Discord Developer Portal:
   - Go to your application → **Bot** → **Privileged Gateway Intents**
   - Enable **Message Content Intent**
6. Verify the channel ID matches the channel you expect — enable Developer Mode in Discord (**User Settings → Advanced → Developer Mode**), then right-click the channel → **Copy Channel ID**

### Bot Starts but Immediately Shuts Down

Check the startup logs for validation errors:

```bash
docker logs open-mc-agent-manager | head -20
```

The bot requires all three credentials at startup. If any are missing or blank, the bot will log a warning and refuse to start:

- `AGENT_DISCORD_BOT_TOKEN` — the bot token from the Discord Developer Portal
- `AGENT_DISCORD_CHANNEL_ID` — the numeric channel ID
- `AGENT_ANTHROPIC_API_KEY` — your Anthropic API key

### Tool Execution Fails

1. Verify `minecraft-wrapper` is running and accessible:
   ```bash
   docker logs open-mc-server
   ```
2. Test the wrapper API directly from inside the Docker network:
   ```bash
   docker exec open-mc-agent-manager curl -s http://minecraft-wrapper:8092/api/server/status
   ```
3. Ensure the `MINECRAFT_WRAPPER_URL` is correct (default: `http://minecraft-wrapper:8092`)

### Anthropic API Errors

1. Verify the Anthropic API key is valid and has not expired
2. Check for rate limiting in the logs — Anthropic returns HTTP 429 when rate-limited
3. Ensure network connectivity to `api.anthropic.com` from within the container:
   ```bash
   docker exec open-mc-agent-manager curl -s https://api.anthropic.com
   ```

### Confirmation Reaction Not Working

1. Ensure the bot has **Add Reactions** permission in the channel
2. Only the user who originally requested the action can confirm it — other users' reactions are ignored
3. Pending confirmations expire after 5 minutes; if the reaction is added after expiration, it will be ignored
4. Check that the **Guild Message Reactions** intent is working — the bot enables `GUILD_MESSAGE_REACTIONS` by default
