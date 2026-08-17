package com.openmc.agentmanager.service;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiscordBotService Tests")
class DiscordBotServiceTest {

    private static final String CHANNEL_ID = "channel-123";
    private static final String USER_ID = "user-456";
    private static final String USERNAME = "steve";
    private static final String MESSAGE_ID = "message-789";
    private static final String ERROR_ON_MESSAGE =
            "❌ An error occurred while processing your request. Please try again.";
    private static final String ERROR_ON_EXECUTION =
            "❌ An error occurred while executing the action. Please try again.";

    @Mock
    private AgentService agentService;

    @Mock
    private ConfirmationService confirmationService;

    private DiscordBotService discordBotService;

    @BeforeEach
    void setUp() {
        discordBotService = new DiscordBotService(agentService, confirmationService);
        ReflectionTestUtils.setField(discordBotService, "channelId", CHANNEL_ID);
        ReflectionTestUtils.setField(discordBotService, "botToken", "token");
        ReflectionTestUtils.setField(discordBotService, "anthropicApiKey", "api-key");
        ReflectionTestUtils.setField(discordBotService, "agentEnabled", true);
        // The service offloads work to a fixed thread pool. Swapping in a same-thread
        // executor makes the submitted logic run inline so it can be asserted on.
        ReflectionTestUtils.setField(discordBotService, "executor", new SameThreadExecutorService());
    }

    // ---------------------------------------------------------------- init()

    @Test
    @DisplayName("Should not start the Discord bot when the agent is disabled")
    void shouldNotStartBotWhenAgentDisabled() {
        ReflectionTestUtils.setField(discordBotService, "agentEnabled", false);

        discordBotService.init();

        assertNull(ReflectionTestUtils.getField(discordBotService, "jda"));
    }

    @Test
    @DisplayName("Should not start the Discord bot when the bot token is blank")
    void shouldNotStartBotWhenTokenBlank() {
        ReflectionTestUtils.setField(discordBotService, "botToken", "  ");

        discordBotService.init();

        assertNull(ReflectionTestUtils.getField(discordBotService, "jda"));
    }

    @Test
    @DisplayName("Should not start the Discord bot when the channel ID is blank")
    void shouldNotStartBotWhenChannelIdBlank() {
        ReflectionTestUtils.setField(discordBotService, "channelId", "");

        discordBotService.init();

        assertNull(ReflectionTestUtils.getField(discordBotService, "jda"));
    }

    @Test
    @DisplayName("Should not start the Discord bot when the Anthropic API key is blank")
    void shouldNotStartBotWhenApiKeyBlank() {
        ReflectionTestUtils.setField(discordBotService, "anthropicApiKey", "");

        discordBotService.init();

        assertNull(ReflectionTestUtils.getField(discordBotService, "jda"));
    }

    // ------------------------------------------------------ onMessageReceived

    @Test
    @DisplayName("Should ignore messages authored by a bot")
    void shouldIgnoreMessagesFromBots() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        User author = mock(User.class);
        when(event.getAuthor()).thenReturn(author);
        when(author.isBot()).thenReturn(true);

        discordBotService.onMessageReceived(event);

        verifyNoInteractions(agentService);
    }

    @Test
    @DisplayName("Should ignore messages sent outside the configured channel")
    void shouldIgnoreMessagesFromOtherChannels() {
        MessageReceivedEvent event = messageEvent("hello");
        when(event.getChannel().getId()).thenReturn("some-other-channel");

        discordBotService.onMessageReceived(event);

        verifyNoInteractions(agentService);
    }

    @Test
    @DisplayName("Should ignore blank messages")
    void shouldIgnoreBlankMessages() {
        MessageReceivedEvent event = messageEvent("   ");

        discordBotService.onMessageReceived(event);

        verifyNoInteractions(agentService);
    }

    @Test
    @DisplayName("Should send the agent response directly when no confirmation is required")
    void shouldSendResponseWhenNoConfirmationRequired() {
        MessageReceivedEvent event = messageEvent("how many players are online?");
        MessageCreateAction createAction = stubSendMessage(event.getChannel());
        when(agentService.processMessage("how many players are online?", USERNAME))
                .thenReturn(new AgentService.AgentResponse(
                        "3 players online", false, null, null, null, null, null));

        discordBotService.onMessageReceived(event);

        verify(event.getChannel()).sendMessage("3 players online");
        verify(createAction).queue(any(), any());
        verify(confirmationService, never()).addPendingConfirmation(anyString(), any());
    }

    @Test
    @DisplayName("Should store a pending confirmation for the requesting user when confirmation is required")
    void shouldStorePendingConfirmationWhenConfirmationRequired() {
        MessageReceivedEvent event = messageEvent("stop the server");
        MessageCreateAction createAction = stubSendMessage(event.getChannel());
        List<com.openmc.agentmanager.model.AnthropicResponse.ContentBlock> assistantContent = List.of();
        Map<String, Object> toolInput = Map.of("reason", "maintenance");
        when(agentService.processMessage("stop the server", USERNAME))
                .thenReturn(new AgentService.AgentResponse(
                        "React with ✅ to stop the server", true, "stop_server", "tool-use-1",
                        assistantContent, "stop the server", toolInput));

        Message sentMessage = mock(Message.class);
        when(sentMessage.getId()).thenReturn(MESSAGE_ID);
        RestAction<Void> reactionAction = mockRestAction();
        when(sentMessage.addReaction(any())).thenReturn(reactionAction);

        discordBotService.onMessageReceived(event);

        // The pending confirmation is only recorded once Discord confirms the message
        // was sent, so the success callback has to be invoked to reach that code.
        ArgumentCaptor<Consumer<? super Message>> successCaptor = consumerCaptor();
        verify(createAction).queue(successCaptor.capture(), any());
        successCaptor.getValue().accept(sentMessage);

        ArgumentCaptor<ConfirmationService.PendingConfirmation> pendingCaptor =
                ArgumentCaptor.forClass(ConfirmationService.PendingConfirmation.class);
        verify(confirmationService).addPendingConfirmation(eq(MESSAGE_ID), pendingCaptor.capture());

        ConfirmationService.PendingConfirmation pending = pendingCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("tool-use-1", pending.toolUseId());
        org.junit.jupiter.api.Assertions.assertEquals("stop_server", pending.toolName());
        org.junit.jupiter.api.Assertions.assertEquals("stop the server", pending.userMessage());
        org.junit.jupiter.api.Assertions.assertEquals(assistantContent, pending.assistantContent());
        org.junit.jupiter.api.Assertions.assertEquals(CHANNEL_ID, pending.channelId());
        org.junit.jupiter.api.Assertions.assertEquals(USER_ID, pending.requestingUserId());
        org.junit.jupiter.api.Assertions.assertEquals(USERNAME, pending.discordUsername());
        org.junit.jupiter.api.Assertions.assertEquals(toolInput, pending.toolInput());
        assertNotNull(pending.createdAt());

        verify(sentMessage).addReaction(any());
    }

    @Test
    @DisplayName("Should report an error to the channel when the agent throws")
    void shouldSendErrorMessageWhenAgentThrows() {
        MessageReceivedEvent event = messageEvent("break something");
        stubSendMessage(event.getChannel());
        when(agentService.processMessage(anyString(), anyString()))
                .thenThrow(new RuntimeException("agent exploded"));

        discordBotService.onMessageReceived(event);

        verify(event.getChannel()).sendMessage(ERROR_ON_MESSAGE);
    }

    // --------------------------------------------------- onMessageReactionAdd

    @Test
    @DisplayName("Should ignore reactions added by a bot")
    void shouldIgnoreReactionsFromBots() {
        MessageReactionAddEvent event = mock(MessageReactionAddEvent.class);
        User user = mock(User.class);
        when(event.getUser()).thenReturn(user);
        when(user.isBot()).thenReturn(true);

        discordBotService.onMessageReactionAdd(event);

        verifyNoInteractions(confirmationService);
    }

    @Test
    @DisplayName("Should ignore reactions added outside the configured channel")
    void shouldIgnoreReactionsFromOtherChannels() {
        MessageReactionAddEvent event = reactionEvent("✅");
        when(event.getChannel().getId()).thenReturn("some-other-channel");

        discordBotService.onMessageReactionAdd(event);

        verifyNoInteractions(confirmationService);
    }

    @Test
    @DisplayName("Should ignore reactions other than the checkmark")
    void shouldIgnoreNonCheckmarkReactions() {
        MessageReactionAddEvent event = reactionEvent("❌");

        discordBotService.onMessageReactionAdd(event);

        verifyNoInteractions(confirmationService);
    }

    @Test
    @DisplayName("Should not execute a tool when no pending confirmation belongs to the reacting user")
    void shouldNotExecuteToolWhenNoPendingConfirmationForUser() {
        MessageReactionAddEvent event = reactionEvent("✅");
        when(confirmationService.consumeIfRequestingUser(MESSAGE_ID, USER_ID)).thenReturn(null);

        discordBotService.onMessageReactionAdd(event);

        verify(confirmationService).consumeIfRequestingUser(MESSAGE_ID, USER_ID);
        verifyNoInteractions(agentService);
    }

    @Test
    @DisplayName("Should execute the confirmed tool and send its response")
    void shouldExecuteConfirmedToolAndSendResponse() {
        MessageReactionAddEvent event = reactionEvent("✅");
        GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
        MessageCreateAction createAction = stubSendMessage(channel);

        List<com.openmc.agentmanager.model.AnthropicResponse.ContentBlock> assistantContent = List.of();
        Map<String, Object> toolInput = Map.of("reason", "maintenance");
        ConfirmationService.PendingConfirmation pending = new ConfirmationService.PendingConfirmation(
                "tool-use-1", "stop_server", "stop the server", assistantContent,
                CHANNEL_ID, USER_ID, USERNAME, toolInput, java.time.Instant.now());
        when(confirmationService.consumeIfRequestingUser(MESSAGE_ID, USER_ID)).thenReturn(pending);
        when(agentService.executeToolAndRespond("stop the server", assistantContent,
                "tool-use-1", "stop_server", USERNAME, toolInput))
                .thenReturn(new AgentService.AgentResponse(
                        "Server stopped", false, null, null, null, null, null));

        discordBotService.onMessageReactionAdd(event);

        verify(agentService).executeToolAndRespond("stop the server", assistantContent,
                "tool-use-1", "stop_server", USERNAME, toolInput);
        verify(channel).sendMessage("Server stopped");
        verify(createAction).queue(any(), any());
    }

    @Test
    @DisplayName("Should report an error to the channel when confirmed tool execution throws")
    void shouldSendErrorMessageWhenToolExecutionThrows() {
        MessageReactionAddEvent event = reactionEvent("✅");
        GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
        stubSendMessage(channel);

        ConfirmationService.PendingConfirmation pending = new ConfirmationService.PendingConfirmation(
                "tool-use-1", "stop_server", "stop the server", List.of(),
                CHANNEL_ID, USER_ID, USERNAME, Map.of(), java.time.Instant.now());
        when(confirmationService.consumeIfRequestingUser(MESSAGE_ID, USER_ID)).thenReturn(pending);
        when(agentService.executeToolAndRespond(anyString(), any(), anyString(), anyString(),
                anyString(), any())).thenThrow(new RuntimeException("tool exploded"));

        discordBotService.onMessageReactionAdd(event);

        verify(channel).sendMessage(ERROR_ON_EXECUTION);
    }

    // ------------------------------------------------------------- shutdown()

    @Test
    @DisplayName("Should shut down cleanly when the bot was never started")
    void shouldShutDownCleanlyWhenBotNeverStarted() {
        discordBotService.shutdown();

        ExecutorService executor = (ExecutorService)
                ReflectionTestUtils.getField(discordBotService, "executor");
        assertNotNull(executor);
        org.junit.jupiter.api.Assertions.assertTrue(executor.isShutdown());
    }

    // ----------------------------------------------------------------- helpers

    /**
     * Build a message event in the configured channel from a non-bot author.
     */
    private MessageReceivedEvent messageEvent(String content) {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        User author = mock(User.class);
        when(author.isBot()).thenReturn(false);
        when(author.getId()).thenReturn(USER_ID);
        when(author.getName()).thenReturn(USERNAME);
        when(event.getAuthor()).thenReturn(author);

        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        when(channel.getId()).thenReturn(CHANNEL_ID);
        when(channel.sendTyping()).thenReturn(mockRestAction());
        when(event.getChannel()).thenReturn(channel);

        Message message = mock(Message.class);
        when(message.getContentRaw()).thenReturn(content);
        when(event.getMessage()).thenReturn(message);
        return event;
    }

    /**
     * Build a reaction event in the configured channel from a non-bot user.
     */
    private MessageReactionAddEvent reactionEvent(String reactionCode) {
        MessageReactionAddEvent event = mock(MessageReactionAddEvent.class);
        User user = mock(User.class);
        when(user.isBot()).thenReturn(false);
        when(event.getUser()).thenReturn(user);
        when(event.getUserId()).thenReturn(USER_ID);
        when(event.getMessageId()).thenReturn(MESSAGE_ID);

        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        when(channel.getId()).thenReturn(CHANNEL_ID);
        when(event.getChannel()).thenReturn(channel);

        GuildMessageChannel guildChannel = mock(GuildMessageChannel.class);
        when(guildChannel.sendTyping()).thenReturn(mockRestAction());
        when(channel.asGuildMessageChannel()).thenReturn(guildChannel);

        MessageReaction reaction = mock(MessageReaction.class);
        EmojiUnion emoji = mock(EmojiUnion.class);
        when(emoji.getAsReactionCode()).thenReturn(reactionCode);
        when(emoji.getName()).thenReturn(reactionCode);
        when(reaction.getEmoji()).thenReturn(emoji);
        when(event.getReaction()).thenReturn(reaction);
        return event;
    }

    private MessageCreateAction stubSendMessage(net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
        MessageCreateAction createAction = mock(MessageCreateAction.class);
        when(channel.sendMessage(anyString())).thenReturn(createAction);
        return createAction;
    }

    @SuppressWarnings("unchecked")
    private <T> RestAction<T> mockRestAction() {
        return mock(RestAction.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Consumer<? super Message>> consumerCaptor() {
        return ArgumentCaptor.forClass(Consumer.class);
    }

    /**
     * Executor that runs submitted work on the calling thread so the service's
     * offloaded logic is observable without any waiting.
     */
    private static final class SameThreadExecutorService extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
