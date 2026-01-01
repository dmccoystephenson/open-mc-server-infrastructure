package com.openmc.webapp.startup;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.rcon.RconClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Checks that the Minecraft server RCON is available before the webapp fully starts.
 * This prevents the webapp from caching an "offline" state when the server is still starting up.
 * Can be disabled by setting 'minecraft.server.wait-for-ready=false'
 */
@Component
@ConditionalOnProperty(name = "minecraft.server.wait-for-ready", havingValue = "true", matchIfMissing = true)
public class RconReadinessChecker implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(RconReadinessChecker.class);
    
    private final ServerConfig serverConfig;
    private final int maxRetries;
    private final int initialDelayMs;
    private final int maxDelayMs;
    private final double backoffMultiplier;
    
    public RconReadinessChecker(
            ServerConfig serverConfig,
            @Value("${minecraft.server.readiness.max-retries:30}") int maxRetries,
            @Value("${minecraft.server.readiness.initial-delay-ms:1000}") int initialDelayMs,
            @Value("${minecraft.server.readiness.max-delay-ms:10000}") int maxDelayMs,
            @Value("${minecraft.server.readiness.backoff-multiplier:1.5}") double backoffMultiplier) {
        this.serverConfig = serverConfig;
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.backoffMultiplier = backoffMultiplier;
    }
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("Waiting for Minecraft server RCON to become available at {}:{}...", 
                    serverConfig.getHost(), serverConfig.getRconPort());
        
        int attempt = 0;
        int delayMs = initialDelayMs;
        
        while (attempt < maxRetries) {
            attempt++;
            
            try {
                // Try to connect and send a simple command
                try (RconClient rcon = new RconClient(
                        serverConfig.getHost(), 
                        serverConfig.getRconPort(), 
                        serverConfig.getRconPassword())) {
                    String response = rcon.sendCommand("list");
                    
                    // If we get here, RCON is available
                    logger.info("Minecraft server RCON is ready! (attempt {}/{})", attempt, maxRetries);
                    logger.info("Server response: {}", response);
                    return;
                }
            } catch (IOException e) {
                logger.debug("RCON connection attempt {}/{} failed: {}", 
                            attempt, maxRetries, e.getMessage());
                
                if (attempt >= maxRetries) {
                    logger.error("Failed to connect to Minecraft server RCON after {} attempts. " +
                                "The server may not be ready yet. Webapp will start but may show " +
                                "the server as offline until it becomes available.", maxRetries);
                    // Don't throw exception - let the webapp start anyway
                    // The scheduled refresh will eventually detect when the server is ready
                    return;
                }
                
                // Wait before retrying with exponential backoff
                logger.info("Waiting {}ms before retry {}/{}...", delayMs, attempt + 1, maxRetries);
                Thread.sleep(delayMs);
                
                // Increase delay with exponential backoff, capped at maxDelayMs
                delayMs = Math.min((int)(delayMs * backoffMultiplier), maxDelayMs);
            }
        }
    }
}
