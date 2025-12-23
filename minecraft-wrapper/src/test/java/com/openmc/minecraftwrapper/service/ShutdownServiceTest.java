package com.openmc.minecraftwrapper.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShutdownService Tests")
class ShutdownServiceTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private ShutdownService shutdownService;

    @Test
    @DisplayName("Should send countdown messages before shutdown")
    void shouldSendCountdownMessagesBeforeShutdown() {
        Runnable stopCommand = mock(Runnable.class);

        // We need to mock the sleep behavior, but since it's a private method,
        // we'll just verify the messages and command are called
        shutdownService.performGracefulShutdown(stopCommand);

        verify(messageService, times(1)).sendMessage("Server is shutting down in 30 seconds!", "MINECRAFT");
        verify(messageService, times(1)).sendMessage("Server is shutting down in 20 seconds!", "MINECRAFT");
        verify(messageService, times(1)).sendMessage("Server is shutting down in 10 seconds!", "MINECRAFT");
        verify(messageService, times(1)).sendMessage("Server is shutting down in 5 seconds!", "MINECRAFT");
        verify(stopCommand, times(1)).run();
    }

    @Test
    @DisplayName("Should execute stop command after countdown")
    void shouldExecuteStopCommandAfterCountdown() {
        Runnable stopCommand = mock(Runnable.class);

        shutdownService.performGracefulShutdown(stopCommand);

        // Verify stop command is called after messages
        verify(stopCommand, times(1)).run();
    }
}
