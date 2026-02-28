package com.openmc.agentmanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfirmationService Tests")
class ConfirmationServiceTest {

    @InjectMocks
    private ConfirmationService confirmationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(confirmationService, "startServerRequiresConfirmation", true);
        ReflectionTestUtils.setField(confirmationService, "stopServerRequiresConfirmation", true);
        ReflectionTestUtils.setField(confirmationService, "restartServerRequiresConfirmation", true);
        ReflectionTestUtils.setField(confirmationService, "triggerBackupRequiresConfirmation", true);
    }

    @Test
    @DisplayName("Should require confirmation for start_server when enabled")
    void shouldRequireConfirmationForStartServer() {
        assertTrue(confirmationService.requiresConfirmation("start_server"));
    }

    @Test
    @DisplayName("Should not require confirmation for start_server when disabled")
    void shouldNotRequireConfirmationForStartServerWhenDisabled() {
        ReflectionTestUtils.setField(confirmationService, "startServerRequiresConfirmation", false);
        assertFalse(confirmationService.requiresConfirmation("start_server"));
    }

    @Test
    @DisplayName("Should require confirmation for stop_server when enabled")
    void shouldRequireConfirmationForStopServer() {
        assertTrue(confirmationService.requiresConfirmation("stop_server"));
    }

    @Test
    @DisplayName("Should not require confirmation for stop_server when disabled")
    void shouldNotRequireConfirmationForStopServerWhenDisabled() {
        ReflectionTestUtils.setField(confirmationService, "stopServerRequiresConfirmation", false);
        assertFalse(confirmationService.requiresConfirmation("stop_server"));
    }

    @Test
    @DisplayName("Should require confirmation for restart_server when enabled")
    void shouldRequireConfirmationForRestartServer() {
        assertTrue(confirmationService.requiresConfirmation("restart_server"));
    }

    @Test
    @DisplayName("Should not require confirmation for restart_server when disabled")
    void shouldNotRequireConfirmationForRestartServerWhenDisabled() {
        ReflectionTestUtils.setField(confirmationService, "restartServerRequiresConfirmation", false);
        assertFalse(confirmationService.requiresConfirmation("restart_server"));
    }

    @Test
    @DisplayName("Should never require confirmation for get_server_status")
    void shouldNeverRequireConfirmationForGetServerStatus() {
        assertFalse(confirmationService.requiresConfirmation("get_server_status"));
    }

    @Test
    @DisplayName("Should require confirmation for trigger_backup when enabled")
    void shouldRequireConfirmationForTriggerBackup() {
        assertTrue(confirmationService.requiresConfirmation("trigger_backup"));
    }

    @Test
    @DisplayName("Should not require confirmation for trigger_backup when disabled")
    void shouldNotRequireConfirmationForTriggerBackupWhenDisabled() {
        ReflectionTestUtils.setField(confirmationService, "triggerBackupRequiresConfirmation", false);
        assertFalse(confirmationService.requiresConfirmation("trigger_backup"));
    }

    @Test
    @DisplayName("Should not require confirmation for unknown tools")
    void shouldNotRequireConfirmationForUnknownTools() {
        assertFalse(confirmationService.requiresConfirmation("unknown_tool"));
    }

    @Test
    @DisplayName("Should store and consume pending confirmation")
    void shouldStoreAndConsumePendingConfirmation() {
        ConfirmationService.PendingConfirmation pending = new ConfirmationService.PendingConfirmation(
                "tool-1", "start_server", "start the server", null, "channel-1", "user-1", "testuser", Instant.now());

        confirmationService.addPendingConfirmation("msg-1", pending);
        assertTrue(confirmationService.hasPendingConfirmation("msg-1"));

        ConfirmationService.PendingConfirmation consumed = confirmationService.consumePendingConfirmation("msg-1");
        assertNotNull(consumed);
        assertEquals("start_server", consumed.toolName());
        assertFalse(confirmationService.hasPendingConfirmation("msg-1"));
    }

    @Test
    @DisplayName("Should return null when consuming non-existent confirmation")
    void shouldReturnNullForNonExistentConfirmation() {
        assertNull(confirmationService.consumePendingConfirmation("non-existent"));
    }

    @Test
    @DisplayName("Should not have pending confirmation for unknown message ID")
    void shouldNotHavePendingConfirmationForUnknownId() {
        assertFalse(confirmationService.hasPendingConfirmation("unknown-id"));
    }

    @Test
    @DisplayName("Should clean up expired confirmations")
    void shouldCleanUpExpiredConfirmations() {
        // Create a confirmation with a timestamp in the past (beyond TTL)
        ConfirmationService.PendingConfirmation expired = new ConfirmationService.PendingConfirmation(
                "tool-1", "start_server", "start the server", null, "channel-1", "user-1", "testuser",
                Instant.now().minusSeconds(600));

        confirmationService.addPendingConfirmation("expired-msg", expired);
        assertTrue(confirmationService.hasPendingConfirmation("expired-msg"));

        confirmationService.cleanupExpiredConfirmations();

        assertFalse(confirmationService.hasPendingConfirmation("expired-msg"));
    }

    @Test
    @DisplayName("Should not clean up non-expired confirmations")
    void shouldNotCleanUpNonExpiredConfirmations() {
        ConfirmationService.PendingConfirmation recent = new ConfirmationService.PendingConfirmation(
                "tool-1", "start_server", "start the server", null, "channel-1", "user-1", "testuser",
                Instant.now());

        confirmationService.addPendingConfirmation("recent-msg", recent);

        confirmationService.cleanupExpiredConfirmations();

        assertTrue(confirmationService.hasPendingConfirmation("recent-msg"));
    }

    @Test
    @DisplayName("Should consume confirmation only for requesting user")
    void shouldConsumeOnlyForRequestingUser() {
        ConfirmationService.PendingConfirmation pending = new ConfirmationService.PendingConfirmation(
                "tool-1", "start_server", "start the server", null, "channel-1", "user-1", "testuser",
                Instant.now());

        confirmationService.addPendingConfirmation("msg-2", pending);

        // Wrong user should not consume
        assertNull(confirmationService.consumeIfRequestingUser("msg-2", "user-2"));
        assertTrue(confirmationService.hasPendingConfirmation("msg-2"));

        // Correct user should consume
        ConfirmationService.PendingConfirmation consumed = confirmationService.consumeIfRequestingUser("msg-2", "user-1");
        assertNotNull(consumed);
        assertEquals("start_server", consumed.toolName());
        assertFalse(confirmationService.hasPendingConfirmation("msg-2"));
    }

    @Test
    @DisplayName("Should return null for null user ID in consumeIfRequestingUser")
    void shouldReturnNullForNullUserInConsumeIfRequestingUser() {
        ConfirmationService.PendingConfirmation pending = new ConfirmationService.PendingConfirmation(
                "tool-1", "start_server", "start the server", null, "channel-1", "user-1", "testuser",
                Instant.now());

        confirmationService.addPendingConfirmation("msg-3", pending);

        assertNull(confirmationService.consumeIfRequestingUser("msg-3", null));
        assertTrue(confirmationService.hasPendingConfirmation("msg-3"));
    }

    @Test
    @DisplayName("Should clean up only expired among mixed confirmations")
    void shouldCleanUpOnlyExpiredAmongMixed() {
        ConfirmationService.PendingConfirmation expired = new ConfirmationService.PendingConfirmation(
                "tool-1", "start_server", "start", null, "channel-1", "user-1", "testuser",
                Instant.now().minusSeconds(600));
        ConfirmationService.PendingConfirmation recent = new ConfirmationService.PendingConfirmation(
                "tool-2", "stop_server", "stop", null, "channel-1", "user-2", "testuser2",
                Instant.now());

        confirmationService.addPendingConfirmation("expired-msg", expired);
        confirmationService.addPendingConfirmation("recent-msg", recent);

        confirmationService.cleanupExpiredConfirmations();

        assertFalse(confirmationService.hasPendingConfirmation("expired-msg"));
        assertTrue(confirmationService.hasPendingConfirmation("recent-msg"));
    }

    @Test
    @DisplayName("Should preserve all fields in PendingConfirmation record")
    void shouldPreserveAllFieldsInPendingConfirmation() {
        Instant now = Instant.now();
        ConfirmationService.PendingConfirmation pending = new ConfirmationService.PendingConfirmation(
                "tool-use-123", "restart_server", "restart please", null, "channel-42", "user-99", "player1", now);

        confirmationService.addPendingConfirmation("msg-fields", pending);
        ConfirmationService.PendingConfirmation consumed = confirmationService.consumePendingConfirmation("msg-fields");

        assertNotNull(consumed);
        assertEquals("tool-use-123", consumed.toolUseId());
        assertEquals("restart_server", consumed.toolName());
        assertEquals("restart please", consumed.userMessage());
        assertEquals("channel-42", consumed.channelId());
        assertEquals("user-99", consumed.requestingUserId());
        assertEquals("player1", consumed.discordUsername());
        assertEquals(now, consumed.createdAt());
    }

    @Test
    @DisplayName("Should allow overwriting a pending confirmation for the same message ID")
    void shouldOverwritePendingConfirmationForSameMessageId() {
        ConfirmationService.PendingConfirmation first = new ConfirmationService.PendingConfirmation(
                "tool-1", "start_server", "start", null, "channel-1", "user-1", "testuser", Instant.now());
        ConfirmationService.PendingConfirmation second = new ConfirmationService.PendingConfirmation(
                "tool-2", "stop_server", "stop", null, "channel-1", "user-2", "testuser2", Instant.now());

        confirmationService.addPendingConfirmation("msg-overwrite", first);
        confirmationService.addPendingConfirmation("msg-overwrite", second);

        ConfirmationService.PendingConfirmation consumed = confirmationService.consumePendingConfirmation("msg-overwrite");
        assertNotNull(consumed);
        assertEquals("stop_server", consumed.toolName());
        assertEquals("user-2", consumed.requestingUserId());
    }

    @Test
    @DisplayName("Should return null for consumeIfRequestingUser with non-existent message")
    void shouldReturnNullForConsumeIfRequestingUserWithNonExistentMessage() {
        assertNull(confirmationService.consumeIfRequestingUser("non-existent", "user-1"));
    }

    @Test
    @DisplayName("Should handle cleanup with no pending confirmations")
    void shouldHandleCleanupWithNoPendingConfirmations() {
        assertDoesNotThrow(() -> confirmationService.cleanupExpiredConfirmations());
    }
}
