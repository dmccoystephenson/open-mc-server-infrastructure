package com.openmc.backupmanager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "backup.script.path=/tmp/test-backup.sh",
    "backup.directory=/tmp/test-backups",
    "backup.max.size.mb=10240"
})
@DisplayName("BackupManagerApplication Tests")
class BackupManagerApplicationTest {

    @Test
    @DisplayName("Should load application context")
    void shouldLoadApplicationContext() {
        // This test verifies that the Spring application context loads successfully
    }
}
