package com.openmc.upgrademanager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "version.check.enabled=false",
    "alerts.upgrade.start=false",
    "alerts.upgrade.complete=false",
    "alerts.upgrade.failure=false",
    "alerts.version.check=false"
})
class UpgradeManagerApplicationTest {

    @Test
    void contextLoads() {
        // Test that the application context loads successfully
    }
}
