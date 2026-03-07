package com.openmc.webapp.startup;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.service.RconService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("RconReadinessChecker Tests")
class RconReadinessCheckerTest {
    
    private ServerConfig serverConfig;
    private RconService rconService;
    private RconReadinessChecker readinessChecker;
    
    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        serverConfig.setHost("localhost");
        serverConfig.setRconPort(25575);
        serverConfig.setRconPassword("test");
        
        // Mock RconService to avoid actual RCON calls during testing
        rconService = mock(RconService.class);
        
        // Use minimal retries and fast timeouts for faster tests
        readinessChecker = new RconReadinessChecker(serverConfig, rconService, 2, 100, 500, 1.5, 500);
    }
    
    @Test
    @DisplayName("Should not throw exception when RCON is unavailable")
    void shouldNotThrowExceptionWhenRconUnavailable() {
        // This test verifies that the readiness checker doesn't prevent startup
        // even when RCON is unavailable - it should just log and continue
        // Use a port that's likely to be closed/unused
        serverConfig.setRconPort(54321);
        
        assertDoesNotThrow(() -> {
            readinessChecker.run(new DefaultApplicationArguments());
        });
        
        // Verify that getServerStatus was never called since RCON never connected
        verify(rconService, never()).getServerStatus();
    }
    
    @Test
    @DisplayName("Should handle invalid server configuration gracefully")
    void shouldHandleInvalidConfigurationGracefully() {
        ServerConfig invalidConfig = new ServerConfig();
        // Use localhost with a closed port for deterministic failure
        invalidConfig.setHost("localhost");
        invalidConfig.setRconPort(54322);
        invalidConfig.setRconPassword("test");
        
        RconService mockRconService = mock(RconService.class);
        
        // Use minimal retries and fast timeouts for faster tests
        RconReadinessChecker checker = new RconReadinessChecker(invalidConfig, mockRconService, 2, 100, 500, 1.5, 500);
        
        // Should not throw exception even with invalid config
        assertDoesNotThrow(() -> {
            checker.run(new DefaultApplicationArguments());
        });
        
        // Verify that getServerStatus was never called since RCON never connected
        verify(mockRconService, never()).getServerStatus();
    }
    
    @Test
    @DisplayName("Should handle interrupted exception gracefully")
    void shouldHandleInterruptedExceptionGracefully() {
        // Set a longer initial delay to ensure we can interrupt during sleep
        RconReadinessChecker checker = new RconReadinessChecker(serverConfig, rconService, 5, 2000, 5000, 1.5, 500);
        
        Thread testThread = new Thread(() -> {
            assertDoesNotThrow(() -> {
                checker.run(new DefaultApplicationArguments());
            });
        });
        
        testThread.start();
        
        // Wait a bit longer to ensure the checker has started and is in sleep
        try {
            Thread.sleep(1000);
            testThread.interrupt();
            testThread.join(3000); // Wait for thread to finish
        } catch (InterruptedException e) {
            fail("Test thread should not be interrupted");
        }
        
        assertFalse(testThread.isAlive(), "Checker should exit gracefully on interrupt");
        // Verify thread was interrupted (the interrupt flag is preserved in the testThread)
        assertTrue(testThread.isInterrupted(), 
                  "Thread interrupt flag should be preserved after graceful exit");
    }
    
    @Test
    @DisplayName("Should handle exceptions other than IOException")
    void shouldHandleNonIOExceptions() {
        ServerConfig badConfig = new ServerConfig();
        // Use an invalid host format to potentially trigger other exceptions
        badConfig.setHost("");
        badConfig.setRconPort(25575);
        badConfig.setRconPassword("test");
        
        RconService mockRconService = mock(RconService.class);
        RconReadinessChecker checker = new RconReadinessChecker(badConfig, mockRconService, 2, 100, 500, 1.5, 500);
        
        // Should not throw any exception, even non-IOException types
        assertDoesNotThrow(() -> {
            checker.run(new DefaultApplicationArguments());
        });
        
        // Verify that getServerStatus was never called since RCON never connected
        verify(mockRconService, never()).getServerStatus();
    }
}
