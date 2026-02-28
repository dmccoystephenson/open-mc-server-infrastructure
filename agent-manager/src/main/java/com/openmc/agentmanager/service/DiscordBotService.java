package com.openmc.agentmanager.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service that manages the Discord bot connection and listens for messages
 * in the configured channel.
 */
@Slf4j
@Service
public class DiscordBotService extends ListenerAdapter {

    private final AgentService agentService;
    private final ConfirmationService confirmationService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${discord.bot.token:}")
    private String botToken;

    @Value("${discord.channel.id:}")
    private String channelId;

    @Value("${agent.enabled:false}")
    private boolean agentEnabled;

    private JDA jda;

    public DiscordBotService(AgentService agentService, ConfirmationService confirmationService) {
        this.agentService = agentService;
        this.confirmationService = confirmationService;
    }

    @PostConstruct
    public void init() {
        if (!agentEnabled) {
            log.info("Agent manager is disabled (agent.enabled=false). Discord bot will not start.");
            return;
        }

        if (botToken == null || botToken.isBlank()) {
            log.warn("Discord bot token is not configured. Discord bot will not start.");
            return;
        }

        if (channelId == null || channelId.isBlank()) {
            log.warn("Discord channel ID is not configured. Discord bot will not start.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(botToken)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGE_REACTIONS)
                    .addEventListeners(this)
                    .build();
            log.info("Discord bot started successfully, listening on channel: {}", channelId);
        } catch (Exception e) {
            log.error("Failed to start Discord bot", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (jda != null) {
            log.info("Shutting down Discord bot");
            jda.shutdown();
        }
        executor.shutdown();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) {
            return;
        }

        // Only respond in the configured channel
        if (!event.getChannel().getId().equals(channelId)) {
            return;
        }

        String userMessage = event.getMessage().getContentRaw();
        if (userMessage.isBlank()) {
            return;
        }

        String userId = event.getAuthor().getId();
        log.info("Received Discord message from {}: {}", event.getAuthor().getName(), userMessage);

        MessageChannel channel = event.getChannel();
        channel.sendTyping().queue();

        // Offload processing to a dedicated executor to avoid blocking JDA's event thread
        executor.submit(() -> {
            try {
                AgentService.AgentResponse response = agentService.processMessage(userMessage);

                if (response.requiresConfirmation()) {
                    // Send confirmation message and add reaction
                    channel.sendMessage(response.textResponse()).queue(sentMessage -> {
                        sentMessage.addReaction(Emoji.fromUnicode("✅")).queue();
                        // Store pending confirmation with requesting user ID
                        confirmationService.addPendingConfirmation(
                                sentMessage.getId(),
                                new ConfirmationService.PendingConfirmation(
                                        response.toolUseId(),
                                        response.toolName(),
                                        response.userMessage(),
                                        response.assistantContent(),
                                        channelId,
                                        userId,
                                        Instant.now()
                                )
                        );
                    });
                } else {
                    channel.sendMessage(response.textResponse()).queue();
                }
            } catch (Exception e) {
                log.error("Error processing Discord message", e);
                channel.sendMessage("❌ An error occurred while processing your request. Please try again.").queue();
            }
        });
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        // Ignore bot reactions
        if (event.getUser() != null && event.getUser().isBot()) {
            return;
        }

        // Only handle reactions in the configured channel
        if (!event.getChannel().getId().equals(channelId)) {
            return;
        }

        // Only handle ✅ reactions
        if (!"✅".equals(event.getReaction().getEmoji().getAsReactionCode()) &&
                !"U+2705".equals(event.getReaction().getEmoji().getAsReactionCode()) &&
                !"✅".equals(event.getReaction().getEmoji().getName())) {
            return;
        }

        String messageId = event.getMessageId();
        String reactingUserId = event.getUserId();

        // Peek at the pending confirmation to verify user before consuming
        if (!confirmationService.hasPendingConfirmation(messageId)) {
            return;
        }

        ConfirmationService.PendingConfirmation pending = confirmationService.consumePendingConfirmation(messageId);

        if (pending == null) {
            return;
        }

        // Ensure only the original requesting user can confirm this action
        if (reactingUserId == null || !reactingUserId.equals(pending.requestingUserId())) {
            // Put it back — wrong user reacted
            confirmationService.addPendingConfirmation(messageId, pending);
            return;
        }

        log.info("Confirmation received for tool: {}", pending.toolName());

        MessageChannel channel = event.getChannel().asGuildMessageChannel();
        channel.sendTyping().queue();

        // Offload execution to a dedicated executor
        executor.submit(() -> {
            try {
                AgentService.AgentResponse response = agentService.executeToolAndRespond(
                        pending.userMessage(), pending.assistantContent(),
                        pending.toolUseId(), pending.toolName());
                channel.sendMessage(response.textResponse()).queue();
            } catch (Exception e) {
                log.error("Error executing confirmed tool: {}", pending.toolName(), e);
                channel.sendMessage("❌ An error occurred while executing the action. Please try again.").queue();
            }
        });
    }
}
