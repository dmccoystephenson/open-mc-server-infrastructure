package com.openmc.webapp.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ServerConfig Tests")
class ServerConfigTest {

    private ServerConfig serverConfig;

    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
    }

    @Test
    @DisplayName("Should have default host value")
    void shouldHaveDefaultHost() {
        assertEquals("mcserver", serverConfig.getHost());
    }

    @Test
    @DisplayName("Should have default RCON port")
    void shouldHaveDefaultRconPort() {
        assertEquals(25575, serverConfig.getRconPort());
    }

    @Test
    @DisplayName("Should have default RCON password")
    void shouldHaveDefaultRconPassword() {
        assertEquals("minecraft", serverConfig.getRconPassword());
    }

    @Test
    @DisplayName("Should have default MOTD")
    void shouldHaveDefaultMotd() {
        assertEquals("A Private Minecraft Server", serverConfig.getMotd());
    }

    @Test
    @DisplayName("Should have default max players")
    void shouldHaveDefaultMaxPlayers() {
        assertEquals(20, serverConfig.getMaxPlayers());
    }

    @Test
    @DisplayName("Should have default admin username")
    void shouldHaveDefaultAdminUsername() {
        assertEquals("admin", serverConfig.getAdminUsername());
    }

    @Test
    @DisplayName("Should have default admin password")
    void shouldHaveDefaultAdminPassword() {
        assertEquals("admin", serverConfig.getAdminPassword());
    }

    @Test
    @DisplayName("Should have empty Dynmap URL by default")
    void shouldHaveEmptyDynmapUrl() {
        assertEquals("", serverConfig.getDynmapUrl());
    }

    @Test
    @DisplayName("Should have empty BlueMap URL by default")
    void shouldHaveEmptyBluemapUrl() {
        assertEquals("", serverConfig.getBluemapUrl());
    }

    @Test
    @DisplayName("Should allow setting host")
    void shouldAllowSettingHost() {
        serverConfig.setHost("testhost");
        assertEquals("testhost", serverConfig.getHost());
    }

    @Test
    @DisplayName("Should allow setting RCON port")
    void shouldAllowSettingRconPort() {
        serverConfig.setRconPort(12345);
        assertEquals(12345, serverConfig.getRconPort());
    }

    @Test
    @DisplayName("Should allow setting RCON password")
    void shouldAllowSettingRconPassword() {
        serverConfig.setRconPassword("newpassword");
        assertEquals("newpassword", serverConfig.getRconPassword());
    }

    @Test
    @DisplayName("Should allow setting MOTD")
    void shouldAllowSettingMotd() {
        serverConfig.setMotd("Test Server");
        assertEquals("Test Server", serverConfig.getMotd());
    }

    @Test
    @DisplayName("Should allow setting max players")
    void shouldAllowSettingMaxPlayers() {
        serverConfig.setMaxPlayers(50);
        assertEquals(50, serverConfig.getMaxPlayers());
    }

    @Test
    @DisplayName("Should allow setting admin username")
    void shouldAllowSettingAdminUsername() {
        serverConfig.setAdminUsername("testadmin");
        assertEquals("testadmin", serverConfig.getAdminUsername());
    }

    @Test
    @DisplayName("Should allow setting admin password")
    void shouldAllowSettingAdminPassword() {
        serverConfig.setAdminPassword("testpass");
        assertEquals("testpass", serverConfig.getAdminPassword());
    }

    @Test
    @DisplayName("Should allow setting Dynmap URL")
    void shouldAllowSettingDynmapUrl() {
        serverConfig.setDynmapUrl("http://example.com/dynmap");
        assertEquals("http://example.com/dynmap", serverConfig.getDynmapUrl());
    }

    @Test
    @DisplayName("Should allow setting BlueMap URL")
    void shouldAllowSettingBluemapUrl() {
        serverConfig.setBluemapUrl("http://example.com/bluemap");
        assertEquals("http://example.com/bluemap", serverConfig.getBluemapUrl());
    }

    @Test
    @DisplayName("Should have default refresh interval of 30 minutes")
    void shouldHaveDefaultRefreshInterval() {
        assertEquals(1800000, serverConfig.getRefreshIntervalMs());
    }

    @Test
    @DisplayName("Should allow setting refresh interval")
    void shouldAllowSettingRefreshInterval() {
        serverConfig.setRefreshIntervalMs(60000);
        assertEquals(60000, serverConfig.getRefreshIntervalMs());
    }
}
