package com.openmc.minecraftwrapper.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShutdownService Tests")
class ShutdownServiceTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private ShutdownService shutdownService;

    @BeforeEach
    void setUp() {
        // Disable countdown to avoid 30 second test delay
        ReflectionTestUtils.setField(shutdownService, "countdownEnabled", false);
    }

    @Test
    @DisplayName("Should execute stop command when countdown disabled")
    void shouldExecuteStopCommandWhenCountdownDisabled() {
        Runnable stopCommand = mock(Runnable.class);

        shutdownService.performGracefulShutdown(stopCommand);

        // Verify stop command is called without messages
        verify(stopCommand, times(1)).run();
        verify(messageService, never()).sendMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("Should send countdown messages when enabled")
    void shouldSendCountdownMessagesWhenEnabled() {
        // Re-enable countdown for this specific test
        ReflectionTestUtils.setField(shutdownService, "countdownEnabled", true);
        Runnable stopCommand = mock(Runnable.class);

        // Note: This test will take 30 seconds to run
        shutdownService.performGracefulShutdown(stopCommand);

        verify(messageService, times(1)).sendMessage("Server is shutting down in 30 seconds!", "MINECRAFT");
        verify(messageService, times(1)).sendMessage("Server is shutting down in 20 seconds!", "MINECRAFT");
        verify(messageService, times(1)).sendMessage("Server is shutting down in 10 seconds!", "MINECRAFT");
        verify(messageService, times(1)).sendMessage("Server is shutting down in 5 seconds!", "MINECRAFT");
        verify(stopCommand, times(1)).run();
    }
}
