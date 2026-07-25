package com.openmc.backupmanager.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how {@code backup.schedule} controls registration of the
 * {@link BackupService#performScheduledBackup()} cron task.
 *
 * <p>Spring treats the cron value {@code "-"} ({@code Scheduled.CRON_DISABLED}) as
 * "never run", which is the only supported way to switch scheduled backups off —
 * {@code @EnableScheduling} on the application class cannot be disabled by a property.
 */
@DisplayName("Backup schedule configuration")
class BackupScheduleTest {

    private static boolean hasScheduledBackupTask(ScheduledTaskHolder taskHolder) {
        Set<ScheduledTask> tasks = taskHolder.getScheduledTasks();
        return tasks.stream()
                .map(task -> task.getTask().getRunnable().toString())
                .anyMatch(description -> description.contains("performScheduledBackup"));
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {
        "backup.directory=/tmp/test-backups",
        "source.directory=/tmp/test-mcserver",
        "backup.schedule=0 0 2 * * ?"
    })
    @DisplayName("with a cron expression")
    class WithCronExpression {

        @Autowired
        private ScheduledTaskHolder taskHolder;

        @MockBean
        private RestTemplate restTemplate;

        @Test
        @DisplayName("Should register the scheduled backup task")
        void shouldRegisterScheduledBackupTask() {
            assertTrue(hasScheduledBackupTask(taskHolder),
                    "performScheduledBackup should be registered when backup.schedule is a cron expression");
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {
        "backup.directory=/tmp/test-backups",
        "source.directory=/tmp/test-mcserver",
        "backup.schedule=-"
    })
    @DisplayName("with the disabled marker \"-\"")
    class WithDisabledMarker {

        @Autowired
        private ScheduledTaskHolder taskHolder;

        @MockBean
        private RestTemplate restTemplate;

        @Test
        @DisplayName("Should not register the scheduled backup task")
        void shouldNotRegisterScheduledBackupTask() {
            assertFalse(hasScheduledBackupTask(taskHolder),
                    "performScheduledBackup should not be registered when backup.schedule is \"-\"");
        }
    }
}
