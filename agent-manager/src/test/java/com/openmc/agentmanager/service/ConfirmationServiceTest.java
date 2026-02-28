package com.openmc.agentmanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    @DisplayName("Should require confirmation for restart_server when enabled")
    void shouldRequireConfirmationForRestartServer() {
        assertTrue(confirmationService.requiresConfirmation("restart_server"));
    }

    @Test
    @DisplayName("Should require confirmation for unknown tools")
    void shouldRequireConfirmationForUnknownTools() {
        assertTrue(confirmationService.requiresConfirmation("unknown_tool"));
    }

    @Test
    @DisplayName("Should store and consume pending confirmation")
    void shouldStoreAndConsumePendingConfirmation() {
        ConfirmationService.PendingConfirmation pending = new ConfirmationService.PendingConfirmation(
                "tool-1", "start_server", "start the server", null, "channel-1");

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
}
