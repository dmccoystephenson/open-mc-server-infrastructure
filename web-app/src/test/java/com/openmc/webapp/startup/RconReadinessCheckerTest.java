package com.openmc.webapp.startup;

import com.openmc.webapp.config.ServerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RconReadinessChecker Tests")
class RconReadinessCheckerTest {
    
    private ServerConfig serverConfig;
    private RconReadinessChecker readinessChecker;
    
    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        serverConfig.setHost("localhost");
        serverConfig.setRconPort(25575);
        serverConfig.setRconPassword("test");
        // Use minimal retries for faster tests
        readinessChecker = new RconReadinessChecker(serverConfig, 2, 100, 500, 1.5);
    }
    
    @Test
    @DisplayName("Should not throw exception when RCON is unavailable")
    void shouldNotThrowExceptionWhenRconUnavailable() {
        // This test verifies that the readiness checker doesn't prevent startup
        // even when RCON is unavailable - it should just log and continue
        assertDoesNotThrow(() -> {
            readinessChecker.run(new DefaultApplicationArguments());
        });
    }
    
    @Test
    @DisplayName("Should handle invalid server configuration gracefully")
    void shouldHandleInvalidConfigurationGracefully() {
        ServerConfig invalidConfig = new ServerConfig();
        invalidConfig.setHost("invalid-host-that-does-not-exist");
        invalidConfig.setRconPort(25575); // Use valid port number
        invalidConfig.setRconPassword("test");
        
        // Use minimal retries for faster tests
        RconReadinessChecker checker = new RconReadinessChecker(invalidConfig, 2, 100, 500, 1.5);
        
        // Should not throw exception even with invalid config
        assertDoesNotThrow(() -> {
            checker.run(new DefaultApplicationArguments());
        });
    }
}
