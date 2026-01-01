# Discord Notifications for GitHub Repository Activity

This repository includes automated Discord notifications for various GitHub repository activities to keep the community informed about project developments.

## Supported Events

The Discord notification system sends alerts for the following GitHub events:

### Issues
- **Opened**: When a new issue is created
- **Closed**: When an issue is resolved
- **Reopened**: When a closed issue is reopened

**Note**: Label events are not included to avoid notification fatigue. If you need label notifications, you can add them back by editing the workflow file.

### Pull Requests
- **Opened**: When a new pull request is created
- **Closed**: When a pull request is closed (not merged)
- **Merged**: When a pull request is successfully merged
- **Reopened**: When a closed pull request is reopened
- **Ready for Review**: When a draft pull request is marked ready for review
- **Review Requested**: When a review is requested from a team member

### Push Events
- **Commits to main**: When commits are pushed to the main branch
- **Commits to develop**: When commits are pushed to the develop branch

### Releases
- **Published**: When a new release is published
- **Created**: When a release is created

## Setup Instructions

### Step 1: Create a Discord Webhook

1. Open your Discord server and navigate to the channel where you want notifications
2. Click on the channel settings (gear icon)
3. Select **Integrations** from the left sidebar
4. Click on **Webhooks** and then **New Webhook**
5. Give your webhook a name (e.g., "GitHub Notifications")
6. Optionally, customize the webhook avatar
7. Click **Copy Webhook URL** to copy the webhook URL to your clipboard
8. Click **Save Changes**

### Step 2: Add Webhook URL to GitHub Repository

1. Navigate to your GitHub repository
2. Click on **Settings** (repository settings, not your personal settings)
3. In the left sidebar, click on **Secrets and variables** → **Actions**
4. Click on **New repository secret**
5. Name the secret `DISCORD_WEBHOOK_URL`
6. Paste your Discord webhook URL into the value field
7. Click **Add secret**

### Step 3: Verify Setup

Once the webhook is configured, the Discord notifications will automatically start working. You can test it by:
- Creating a test issue or pull request
- Pushing a commit to the main or develop branch
- Publishing a release

The notifications should appear in your designated Discord channel within seconds.

## Notification Format

Each notification includes:
- **Icon Emoji**: Visual indicator of the event type (🆕, ✅, 🔀, 📦, 🚀, etc.)
- **Event Type**: Clear description of what happened
- **Title**: The title of the issue, pull request, or commit message
- **Description**: Additional context or body text (may be truncated by Discord if extremely long)
- **Author**: Who triggered the event (with avatar and link to profile)
- **Link**: Direct link to the issue, pull request, commit, or release
- **Repository Info**: Which repository the event occurred in
- **Timestamp**: When the event occurred (shows creation time for "opened" events, update time for other events)
- **Color Coding**: Different colors for different event types
  - Blue: New issues and pull requests
  - Green: Closed/merged items and releases
  - Orange: Reopened items
  - Purple: Labels and other metadata changes

**Note**: Issue and PR descriptions longer than Discord's 4096 character limit may be truncated. Discord will handle this automatically. Avoid including sensitive information (API keys, passwords, etc.) in issue or PR descriptions as they will be posted to Discord.

## Troubleshooting

### Notifications Not Appearing

If notifications are not appearing in Discord:

1. **Check Secret Configuration**: Ensure `DISCORD_WEBHOOK_URL` is properly set in GitHub repository secrets
2. **Verify Webhook URL**: Make sure the webhook URL is valid and hasn't been deleted from Discord
3. **Check Workflow**: View the workflow runs in the **Actions** tab to see if there are any errors
4. **Webhook Permissions**: Ensure the webhook has permission to post in the target channel
5. **Rate Limiting**: Discord may rate limit webhooks if too many messages are sent too quickly
   - Discord typically allows around **30 messages per minute per webhook** (limits may vary; see Discord's official documentation for current limits)
   - For busy repositories, consider **using separate webhooks for different event types** to distribute traffic
   - You can **disable less important event types** by editing `.github/workflows/discord-notifications.yml`
   - The workflow includes `continue-on-error: true` so rate limiting won't block GitHub events
   - Refer to [Discord's rate limit documentation](https://discord.com/developers/docs/topics/rate-limits) for details

### Testing the Webhook

You can test your webhook directly using curl:

```bash
curl -X POST "YOUR_DISCORD_WEBHOOK_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "embeds": [{
      "title": "Test Notification",
      "description": "This is a test notification from your GitHub repository.",
      "color": 3447003
    }]
  }'
```

Replace `YOUR_DISCORD_WEBHOOK_URL` with your actual webhook URL.

## Customization

To customize which events trigger notifications or how they appear:

1. Edit the workflow file: `.github/workflows/discord-notifications.yml`
2. Modify the `on:` section to add or remove event triggers
3. Adjust embed colors, titles, or descriptions in the workflow steps
4. Commit and push your changes

## Security Notes

- **Keep Webhook URLs Secret**: Never commit webhook URLs directly to your repository
- **Use GitHub Secrets**: Always store webhook URLs in GitHub repository secrets
- **Rotate Webhooks**: If a webhook URL is compromised, delete it in Discord and create a new one
- **Sensitive Information Warning**: Issue and PR descriptions are sent to Discord. Avoid including sensitive information (API keys, passwords, tokens, private data) in public issues or PRs as they will be posted to your Discord channel
- **Content Visibility**: Anyone with access to the Discord channel will see the full content of notifications
- **Monitor Usage**: Regularly check Discord webhook usage to ensure it's not being abused

## Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Discord Webhooks Documentation](https://discord.com/developers/docs/resources/webhook)
- [Discord Webhook GitHub Action](https://github.com/tsickert/discord-webhook)
