package com.openmc.webapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "minecraft.server.host=localhost",
    "minecraft.server.rcon-port=25575",
    "minecraft.server.rcon-password=test",
    "minecraft.server.admin-username=admin",
    "minecraft.server.admin-password=admin"
})
@DisplayName("WebAppApplication Tests")
class WebAppApplicationTest {

    @Test
    @DisplayName("Should load application context")
    void shouldLoadApplicationContext() {
        // This test verifies that the Spring application context loads successfully
    }
}
