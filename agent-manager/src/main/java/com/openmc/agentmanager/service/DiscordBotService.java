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
import java.util.concurrent.TimeUnit;

/**
 * Service that manages the Discord bot connection and listens for messages
 * in the configured channel.
 */
@Slf4j
@Service
public class DiscordBotService extends ListenerAdapter {

    private final AgentService agentService;
    private final ConfirmationService confirmationService;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Value("${discord.bot.token:}")
    private String botToken;

    @Value("${discord.channel.id:}")
    private String channelId;

    @Value("${agent.enabled:false}")
    private boolean agentEnabled;

    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

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

        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            log.warn("Anthropic API key is not configured. Discord bot will not start.");
            return;
        }

        try {
            log.debug("Initializing JDA with gateway intents: GUILD_MESSAGES, MESSAGE_CONTENT, GUILD_MESSAGE_REACTIONS");
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
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) {
            log.debug("Ignoring message from bot user: {}", event.getAuthor().getName());
            return;
        }

        // Only respond in the configured channel
        if (!event.getChannel().getId().equals(channelId)) {
            log.debug("Ignoring message from non-configured channel: {}", event.getChannel().getId());
            return;
        }

        String userMessage = event.getMessage().getContentRaw();
        if (userMessage.isBlank()) {
            log.debug("Ignoring blank message from {}", event.getAuthor().getName());
            return;
        }

        String userId = event.getAuthor().getId();
        String username = event.getAuthor().getName();
        log.info("Received Discord message from {} (ID: {}): {}", username, userId, userMessage);

        MessageChannel channel = event.getChannel();
        channel.sendTyping().queue();

        // Offload processing to a dedicated executor to avoid blocking JDA's event thread
        executor.submit(() -> {
            try {
                log.debug("Processing message from {} on executor thread", username);
                AgentService.AgentResponse response = agentService.processMessage(userMessage, username);

                if (response.requiresConfirmation()) {
                    log.debug("Sending confirmation prompt for tool: {}", response.toolName());
                    // Send confirmation message and add reaction
                    channel.sendMessage(response.textResponse()).queue(sentMessage -> {
                        sentMessage.addReaction(Emoji.fromUnicode("✅")).queue(
                                success -> log.debug("Added ✅ reaction to confirmation message {}", sentMessage.getId()),
                                failure -> log.error("Failed to add ✅ reaction to message {}", sentMessage.getId(), failure)
                        );
                        // Store pending confirmation with requesting user ID and username
                        confirmationService.addPendingConfirmation(
                                sentMessage.getId(),
                                new ConfirmationService.PendingConfirmation(
                                        response.toolUseId(),
                                        response.toolName(),
                                        response.userMessage(),
                                        response.assistantContent(),
                                        channelId,
                                        userId,
                                        username,
                                        response.toolInput(),
                                        Instant.now()
                                )
                        );
                    }, failure -> log.error("RestAction queue returned failure: {}", failure.getMessage(), failure));
                } else {
                    log.debug("Sending direct response (no confirmation required)");
                    channel.sendMessage(response.textResponse()).queue(
                            success -> log.debug("Response sent successfully to channel {}", channelId),
                            failure -> log.error("RestAction queue returned failure: {}", failure.getMessage(), failure)
                    );
                }
            } catch (Exception e) {
                log.error("Error processing Discord message", e);
                channel.sendMessage("❌ An error occurred while processing your request. Please try again.").queue(
                        null,
                        failure -> log.error("Failed to send error message to Discord", failure)
                );
            }
        });
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        // Ignore bot reactions
        if (event.getUser() != null && event.getUser().isBot()) {
            log.debug("Ignoring reaction from bot user");
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
            log.debug("Ignoring non-checkmark reaction: {}", event.getReaction().getEmoji().getName());
            return;
        }

        String messageId = event.getMessageId();
        String reactingUserId = event.getUserId();
        log.debug("Received ✅ reaction on message {} from user {}", messageId, reactingUserId);

        // Atomically consume the pending confirmation only if the requesting user matches
        ConfirmationService.PendingConfirmation pending = confirmationService.consumeIfRequestingUser(messageId, reactingUserId);

        if (pending == null) {
            log.debug("No pending confirmation found for message {} by user {} (either no confirmation exists, user mismatch, or already consumed)", messageId, reactingUserId);
            return;
        }

        log.info("Confirmation received for tool: {} (message: {}, user: {})", pending.toolName(), messageId, reactingUserId);

        MessageChannel channel = event.getChannel().asGuildMessageChannel();
        channel.sendTyping().queue();

        // Offload execution to a dedicated executor
        executor.submit(() -> {
            try {
                log.debug("Executing confirmed tool {} on executor thread", pending.toolName());
                AgentService.AgentResponse response = agentService.executeToolAndRespond(
                        pending.userMessage(), pending.assistantContent(),
                        pending.toolUseId(), pending.toolName(), pending.discordUsername(),
                        pending.toolInput());
                channel.sendMessage(response.textResponse()).queue(
                        success -> log.debug("Tool execution response sent for {}", pending.toolName()),
                        failure -> log.error("Failed to send tool execution response to Discord", failure)
                );
            } catch (Exception e) {
                log.error("Error executing confirmed tool: {}", pending.toolName(), e);
                channel.sendMessage("❌ An error occurred while executing the action. Please try again.").queue(
                        null,
                        failure -> log.error("Failed to send error message to Discord", failure)
                );
            }
        });
    }
}
