package com.openmc.upgrademanager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "env.file.path=/tmp/test.env",
    "alerts.upgrade.start=false",
    "alerts.upgrade.complete=false",
    "alerts.upgrade.failure=false"
})
@DisplayName("UpgradeManagerApplication Tests")
class UpgradeManagerApplicationTest {

    @Test
    @DisplayName("Should load application context")
    void contextLoads() {
        // This test verifies that the Spring application context loads successfully
    }
}
