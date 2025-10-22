package com.openmc.webapp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "ADMIN_USERNAME=testuser",
    "ADMIN_PASSWORD=testpass123"
})
@DisplayName("ServerConfig Integration Tests - Environment Variable Binding")
class ServerConfigIntegrationTest {

    @Autowired
    private ServerConfig serverConfig;

    @Test
    @DisplayName("Should read admin username from environment variable")
    void shouldReadAdminUsernameFromEnv() {
        assertEquals("testuser", serverConfig.getAdminUsername());
    }

    @Test
    @DisplayName("Should read admin password from environment variable")
    void shouldReadAdminPasswordFromEnv() {
        assertEquals("testpass123", serverConfig.getAdminPassword());
    }
}
