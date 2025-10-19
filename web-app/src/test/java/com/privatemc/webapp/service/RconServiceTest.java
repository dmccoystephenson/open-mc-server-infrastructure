package com.privatemc.webapp.service;

import com.privatemc.webapp.config.ServerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
        RconService.ServerStatus status = new RconService.ServerStatus(serverConfig, "Player list");
        
        assertEquals("Test Server MOTD", status.getMotd());
    }

    @Test
    @DisplayName("ServerStatus should have correct max players from config")
    void serverStatusShouldHaveCorrectMaxPlayersFromConfig() {
        serverConfig.setMaxPlayers(50);
        RconService.ServerStatus status = new RconService.ServerStatus(serverConfig, "Player list");
        
        assertEquals(50, status.getMaxPlayers());
    }

    @Test
    @DisplayName("ServerStatus should be online when response is successful")
    void serverStatusShouldBeOnlineWhenResponseIsSuccessful() {
        RconService.ServerStatus status = new RconService.ServerStatus(serverConfig, "There are 0 of a max of 20 players online");
        
        assertTrue(status.isOnline());
    }

    @Test
    @DisplayName("ServerStatus should be offline when response contains error")
    void serverStatusShouldBeOfflineWhenResponseContainsError() {
        RconService.ServerStatus status = new RconService.ServerStatus(serverConfig, "Error: Connection failed");
        
        assertFalse(status.isOnline());
    }
}
