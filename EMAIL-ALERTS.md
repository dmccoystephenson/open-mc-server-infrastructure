# Email Alert Configuration Guide

## Overview

The Minecraft server includes automatic monitoring for server overload conditions. When the server outputs messages like "Can't keep up! Is the server overloaded? Running 3639ms or 72 ticks behind", an email alert will be sent to notify you of the issue.

## Features

- **Automatic Detection**: Monitors server logs in real-time for overload warnings
- **Email Notifications**: Sends alerts via email when issues are detected
- **Spam Prevention**: 5-minute cooldown between alerts to prevent notification spam
- **Flexible Configuration**: Works with system mail or custom SMTP settings

## Quick Setup

### Basic Configuration (Using System Mail)

1. Edit your `.env` file and set the alert email address:
   ```bash
   ALERT_EMAIL=admin@example.com
   ```

2. Ensure your system has a mail transfer agent (MTA) configured, or install mailutils:
   ```bash
   # The Docker container already includes mailutils
   # For host system (if needed for testing):
   sudo apt-get install mailutils
   ```

3. Start or restart your server:
   ```bash
   ./down.sh
   ./up.sh
   ```

### Advanced Configuration (Using Custom SMTP)

For more control over email delivery, configure SMTP settings in your `.env` file:

```bash
# Required: Email address to receive alerts
ALERT_EMAIL=admin@example.com

# Optional: SMTP server configuration
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your-email@gmail.com
SMTP_PASSWORD=your-app-password
SMTP_FROM=minecraft-server@example.com
```

**Note**: For Gmail, you'll need to use an [App Password](https://support.google.com/accounts/answer/185833) rather than your regular password.

## Alert Details

### When Alerts Are Sent

Email alerts are triggered when the Minecraft server logs contain messages matching:
- "Can't keep up! Is the server overloaded?"

### Alert Content

Each email includes:
- The specific overload message from the server log
- Timestamp of the event
- Server hostname
- Recommendations for addressing the issue

### Example Alert Email

```
Subject: Minecraft Server Overload Alert

Your Minecraft server is experiencing performance issues:

[Server thread/WARN]: Can't keep up! Is the server overloaded? Running 3639ms or 72 ticks behind

Timestamp: 2025-10-13 14:30:45
Server: minecraft-server-01

This alert will not repeat for the next 300 seconds to prevent spam.

Please check your server resources and consider:
- Reducing loaded chunks/entities
- Increasing allocated memory
- Upgrading server hardware
- Checking for problematic plugins
```

## Troubleshooting

### No Emails Received

1. **Check Configuration**:
   ```bash
   # View your current configuration
   docker exec private-mc-server env | grep -E "(ALERT_EMAIL|SMTP)"
   ```

2. **Check Logs**:
   ```bash
   # View monitor logs
   docker logs private-mc-server | grep MONITOR
   
   # Look for messages like:
   # [MONITOR] OVERLOAD DETECTED: ...
   # [MONITOR] Sending email alert to ...
   ```

3. **Test Email System**:
   ```bash
   # Test system mail from within the container
   docker exec -it private-mc-server bash -c 'echo "Test" | mail -s "Test" your-email@example.com'
   ```

### Monitor Not Starting

If you don't see monitor logs, check:

1. **ALERT_EMAIL is set**:
   ```bash
   grep ALERT_EMAIL .env
   ```

2. **Container has access to environment variable**:
   ```bash
   docker exec private-mc-server env | grep ALERT_EMAIL
   ```

3. **Restart the server** to pick up configuration changes:
   ```bash
   ./down.sh
   ./up.sh
   ```

## Disabling Email Alerts

To disable email alerts, simply remove or leave empty the `ALERT_EMAIL` setting in your `.env` file:

```bash
ALERT_EMAIL=
```

Then restart the server:
```bash
./down.sh
./up.sh
```

## Performance Impact

The monitoring script has minimal performance impact:
- Runs as a separate background process
- Uses `tail -F` for efficient log monitoring
- Only processes lines containing "Can't keep up"
- Typical CPU usage: <1%

## Security Considerations

- **SMTP Credentials**: If using custom SMTP, your credentials are stored in the `.env` file. Keep this file secure and don't commit it to version control.
- **Email Content**: Alert emails contain server information but no sensitive player data.
- **Cooldown Period**: The 5-minute cooldown prevents abuse but means rapid issues might not generate multiple alerts.

## Advanced Usage

### Customizing Alert Cooldown

Edit `resources/monitor-overload.sh` and change the `ALERT_COOLDOWN` variable (in seconds):

```bash
ALERT_COOLDOWN=300  # Default: 5 minutes (300 seconds)
```

### Multiple Alert Recipients

To send alerts to multiple recipients, configure your MTA to forward emails or use an email alias/distribution list.

### Integrating with Other Systems

The monitoring script can be extended to integrate with other notification systems:
- Modify `send_alert()` function in `resources/monitor-overload.sh`
- Add webhooks (Slack, Discord, etc.)
- Add SMS notifications
- Log to external monitoring systems

## Support

If you encounter issues with email alerts:
1. Check the troubleshooting section above
2. Review the monitor logs: `docker logs private-mc-server | grep MONITOR`
3. Verify your email system is working correctly
4. Open an issue on GitHub with relevant log excerpts
