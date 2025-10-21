package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RconService Tests")
class RconServiceTest {

    @Mock
    private ServerConfig serverConfig;

    private RconService rconService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        serverConfig = new ServerConfig();
        rconService = new RconService(serverConfig);
    }

    @Test
    @DisplayName("Should create RconService with ServerConfig")
    void shouldCreateRconServiceWithServerConfig() {
        assertNotNull(rconService);
    }

    @Test
    @DisplayName("Should return error message when unable to connect")
    void shouldReturnErrorMessageWhenUnableToConnect() {
        String result = rconService.sendCommand("list");
        assertTrue(result.startsWith("Error: Unable to connect to server"));
    }

    @Test
    @DisplayName("Should return ServerStatus with correct configuration")
    void shouldReturnServerStatusWithCorrectConfiguration() {
        RconService.ServerStatus status = rconService.getServerStatus();
        
        assertNotNull(status);
        assertEquals("A Private Minecraft Server", status.getMotd());
        assertEquals(20, status.getMaxPlayers());
        assertFalse(status.isOnline()); // Server is not running in test
    }

    @Test
    @DisplayName("ServerStatus should indicate offline when error occurs")
    void serverStatusShouldIndicateOfflineWhenErrorOccurs() {
        RconService.ServerStatus status = rconService.getServerStatus();
        
        assertFalse(status.isOnline());
        assertTrue(status.getPlayerList().startsWith("Error:"));
    }

    @Test
    @DisplayName("ServerStatus should have correct MOTD from config")
    void serverStatusShouldHaveCorrectMotdFromConfig() {
        serverConfig.setMotd("Test Server MOTD");
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("20.0, 20.0, 20.0", "1024MB", "2048MB", "1024MB", 50.0);
        RconService.ServerStatus status = new RconService.ServerStatus(serverConfig, "Player list", resourceUsage);
        
        assertEquals("Test Server MOTD", status.getMotd());
    }

    @Test
    @DisplayName("ServerStatus should have correct max players from config")
    void serverStatusShouldHaveCorrectMaxPlayersFromConfig() {
        serverConfig.setMaxPlayers(50);
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("20.0, 20.0, 20.0", "1024MB", "2048MB", "1024MB", 50.0);
        RconService.ServerStatus status = new RconService.ServerStatus(serverConfig, "Player list", resourceUsage);
        
        assertEquals(50, status.getMaxPlayers());
    }

    @Test
    @DisplayName("ServerStatus should be online when response is successful")
    void serverStatusShouldBeOnlineWhenResponseIsSuccessful() {
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("20.0, 20.0, 20.0", "1024MB", "2048MB", "1024MB", 50.0);
        RconService.ServerStatus status = new RconService.ServerStatus(serverConfig, "There are 0 of a max of 20 players online", resourceUsage);
        
        assertTrue(status.isOnline());
    }

    @Test
    @DisplayName("ServerStatus should be offline when response contains error")
    void serverStatusShouldBeOfflineWhenResponseContainsError() {
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("N/A", "N/A", "N/A", "N/A", 0.0);
        RconService.ServerStatus status = new RconService.ServerStatus(serverConfig, "Error: Connection failed", resourceUsage);
        
        assertFalse(status.isOnline());
    }
    
    @Test
    @DisplayName("ResourceUsage should store TPS information")
    void resourceUsageShouldStoreTpsInformation() {
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("20.0, 20.0, 20.0", "1024MB", "2048MB", "1024MB", 50.0);
        
        assertEquals("20.0, 20.0, 20.0", resourceUsage.getTps());
    }
    
    @Test
    @DisplayName("ResourceUsage should store memory information")
    void resourceUsageShouldStoreMemoryInformation() {
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("20.0, 20.0, 20.0", "1024MB", "2048MB", "1024MB", 50.0);
        
        assertEquals("1024MB", resourceUsage.getMemoryUsed());
        assertEquals("2048MB", resourceUsage.getMemoryMax());
        assertEquals("1024MB", resourceUsage.getMemoryFree());
        assertEquals(50.0, resourceUsage.getMemoryUsedPercent(), 0.01);
    }
    
    @Test
    @DisplayName("ResourceUsage should handle N/A values")
    void resourceUsageShouldHandleNAValues() {
        RconService.ResourceUsage resourceUsage = new RconService.ResourceUsage("N/A", "N/A", "N/A", "N/A", 0.0);
        
        assertEquals("N/A", resourceUsage.getTps());
        assertEquals("N/A", resourceUsage.getMemoryUsed());
        assertEquals("N/A", resourceUsage.getMemoryMax());
        assertEquals("N/A", resourceUsage.getMemoryFree());
        assertEquals(0.0, resourceUsage.getMemoryUsedPercent(), 0.01);
    }
    
    @Test
    @DisplayName("Should set last fetch time when getting server status")
    void shouldSetLastFetchTimeWhenGettingServerStatus() {
        Instant before = Instant.now();
        rconService.getServerStatus();
        Instant after = Instant.now();
        
        Instant lastFetchTime = rconService.getLastFetchTime();
        assertNotNull(lastFetchTime);
        assertTrue(!lastFetchTime.isBefore(before));
        assertTrue(!lastFetchTime.isAfter(after));
    }
    
    @Test
    @DisplayName("Should cache server status and return same instance")
    void shouldCacheServerStatusAndReturnSameInstance() {
        RconService.ServerStatus firstStatus = rconService.getServerStatus();
        RconService.ServerStatus secondStatus = rconService.getServerStatus();
        
        assertSame(firstStatus, secondStatus);
    }
    
    @Test
    @DisplayName("Should refresh cache after refresh interval")
    void shouldRefreshCacheAfterRefreshInterval() throws InterruptedException {
        // Set a very short refresh interval for testing
        serverConfig.setRefreshIntervalMs(100); // 100ms
        
        RconService.ServerStatus firstStatus = rconService.getServerStatus();
        Instant firstFetchTime = rconService.getLastFetchTime();
        
        // Wait for the refresh interval to pass
        Thread.sleep(150);
        
        RconService.ServerStatus secondStatus = rconService.getServerStatus();
        Instant secondFetchTime = rconService.getLastFetchTime();
        
        // The cache should have been refreshed, so fetch times should be different
        assertNotEquals(firstFetchTime, secondFetchTime);
        assertTrue(secondFetchTime.isAfter(firstFetchTime));
    }
    
    @Test
    @DisplayName("Should not refresh cache before refresh interval")
    void shouldNotRefreshCacheBeforeRefreshInterval() throws InterruptedException {
        // Set a longer refresh interval
        serverConfig.setRefreshIntervalMs(5000); // 5 seconds
        
        RconService.ServerStatus firstStatus = rconService.getServerStatus();
        Instant firstFetchTime = rconService.getLastFetchTime();
        
        // Wait a short time, less than the refresh interval
        Thread.sleep(100);
        
        RconService.ServerStatus secondStatus = rconService.getServerStatus();
        Instant secondFetchTime = rconService.getLastFetchTime();
        
        // The cache should not have been refreshed, so fetch times should be the same
        assertEquals(firstFetchTime, secondFetchTime);
        assertSame(firstStatus, secondStatus);
    }
}
