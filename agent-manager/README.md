# Agent Manager

A Discord-based server management agent for the Minecraft server infrastructure. This Spring Boot application runs as a separate container and provides an agentic AI interface via Discord — users send natural language messages to a designated channel, and the agent interprets the request, selects the appropriate tool, and executes server management actions via the existing `minecraft-wrapper` REST API.

## Features

- **Natural Language Commands**: Send plain English messages to manage the Minecraft server
- **Discord Bot Integration**: Bidirectional communication via a Discord bot (JDA)
- **Anthropic API**: Uses Claude to interpret user intent and select appropriate tools
- **Server Management Tools**: Start, stop, and restart the Minecraft server
- **Confirmation Flow**: Optional per-tool confirmation via Discord reactions (✅)
- **Graceful Shutdown**: Stop and restart leverage minecraft-wrapper's graceful shutdown with player countdown warnings
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
                     └──────────────────┘     │  Wrapper API  │
                                              │  (port 8092)  │
                                              └───────────────┘
```

### Agent Loop

1. User sends a natural language message to the configured Discord channel
2. The agent passes the message to the Anthropic API with a system prompt and tool definitions
3. If the model returns a tool call and confirmation is required, the bot prompts the user to react with ✅
4. Only the original requesting user can confirm the action
5. On confirmation (or immediately if confirmation is disabled), the agent calls the `minecraft-wrapper` REST API
6. The result is sent back to the Anthropic API for a natural language summary, which is posted to Discord

## Configuration

### Environment Variables

The following environment variables can be configured in `.env`:

- `AGENT_CONTAINER_NAME`: Container name (default: `open-mc-agent-manager`)
- `AGENT_PORT`: Port for the agent manager API (default: `8093`)
- `AGENT_DISCORD_BOT_TOKEN`: Discord bot token (**required**)
- `AGENT_DISCORD_CHANNEL_ID`: Discord channel ID to listen on (**required**)
- `AGENT_ANTHROPIC_API_KEY`: Anthropic API key (**required**)
- `AGENT_ENABLED`: Enable/disable the agent manager (default: `false`)

### Per-Tool Confirmation Toggles

- `AGENT_START_SERVER_REQUIRES_CONFIRMATION`: Require confirmation to start (default: `true`)
- `AGENT_STOP_SERVER_REQUIRES_CONFIRMATION`: Require confirmation to stop (default: `true`)
- `AGENT_RESTART_SERVER_REQUIRES_CONFIRMATION`: Require confirmation to restart (default: `true`)

### Discord Bot Setup

To enable the agent manager:

1. Create a Discord application at the [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a bot for your application and copy the bot token
3. Enable the following Privileged Gateway Intents:
   - **Message Content Intent**
   - **Server Members Intent** (optional)
4. Invite the bot to your Discord server with permissions to read and send messages in the target channel
5. Copy the channel ID of the channel you want the bot to listen on (right-click channel → Copy Channel ID, with Developer Mode enabled)
6. Add the credentials to your `.env` file:
   ```bash
   AGENT_DISCORD_BOT_TOKEN=your-bot-token
   AGENT_DISCORD_CHANNEL_ID=your-channel-id
   AGENT_ANTHROPIC_API_KEY=your-anthropic-api-key
   AGENT_ENABLED=true
   ```
7. Restart the infrastructure with `./up.sh`

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

## Security Notes

- Store the Discord bot token, Anthropic API key, and channel ID securely in environment variables, never in code
- The agent is disabled by default (`AGENT_ENABLED=false`) — it must be explicitly enabled
- The system prompt narrowly constrains the agent to server management actions only
- Confirmation is required by default for all destructive actions
- Only the user who requested an action can confirm it via reaction
- Pending confirmations expire after 5 minutes to prevent stale actions
- The Discord bot only listens in the configured channel

## Troubleshooting

### Bot Not Responding

1. Check container logs: `docker logs open-mc-agent-manager`
2. Verify `AGENT_ENABLED=true` in `.env`
3. Verify the bot token and channel ID are correctly configured
4. Ensure the bot has permissions to read and send messages in the target channel
5. Ensure the Message Content Intent is enabled in the Discord Developer Portal

### Tool Execution Fails

1. Verify `minecraft-wrapper` is running and accessible
2. Check the minecraft-wrapper logs: `docker logs open-mc-server`
3. Ensure the `MINECRAFT_WRAPPER_URL` is correct (default: `http://minecraft-wrapper:8092`)

### Anthropic API Errors

1. Verify the Anthropic API key is valid
2. Check for rate limiting in the logs
3. Ensure network connectivity to `api.anthropic.com`
