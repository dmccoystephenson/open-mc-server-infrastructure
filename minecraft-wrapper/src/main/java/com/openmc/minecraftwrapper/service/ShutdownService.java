package com.openmc.minecraftwrapper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for handling graceful server shutdown with player warnings.
 * Note: This service blocks for 30 seconds during shutdown to warn players.
 */
@Slf4j
@Service
public class ShutdownService {

    private final MessageService messageService;
    
    @Value("${shutdown.countdown.enabled:true}")
    private boolean countdownEnabled;

    public ShutdownService(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Performs graceful shutdown with countdown warnings to players.
     * This method blocks for 30 seconds (configurable in tests).
     * @param stopCommand The command to execute after countdown completes
     */
    public void performGracefulShutdown(Runnable stopCommand) {
        log.info("Initiating graceful server shutdown...");

        if (countdownEnabled) {
            // Warn players before shutdown with countdown
            log.info("Warning players of impending shutdown...");

            messageService.sendMessage("Server is shutting down in 30 seconds!", "MINECRAFT");
            sleep(10);

            messageService.sendMessage("Server is shutting down in 20 seconds!", "MINECRAFT");
            sleep(10);

            messageService.sendMessage("Server is shutting down in 10 seconds!", "MINECRAFT");
            sleep(5);

            messageService.sendMessage("Server is shutting down in 5 seconds!", "MINECRAFT");
            sleep(5);
        }

        log.info("Sending stop command to Minecraft server...");
        stopCommand.run();

        log.info("Server shutdown initiated");
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sleep interrupted during shutdown countdown");
        }
    }
}
