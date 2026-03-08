package com.openmc.webapp.service;

import com.openmc.webapp.model.DeploymentRecord;
import com.openmc.webapp.repository.DeploymentRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("DeploymentHistoryService Tests")
@ExtendWith(MockitoExtension.class)
class DeploymentHistoryServiceTest {

    @Mock
    private DeploymentRecordRepository repository;
    private DeploymentHistoryService service;

    @BeforeEach
    void setUp() {
        service = new DeploymentHistoryService(repository);
    }

    @Test
    @DisplayName("Should record a new deployment")
    void shouldRecordNewDeployment() {
        service.recordDeployment("MyPlugin.jar", "SUCCESS", "automated",
                "main", "https://github.com/org/repo", "Plugin deployed successfully");

        ArgumentCaptor<DeploymentRecord> captor = ArgumentCaptor.forClass(DeploymentRecord.class);
        verify(repository).save(captor.capture());

        DeploymentRecord saved = captor.getValue();
        assertEquals("MyPlugin.jar", saved.getPluginName());
        assertEquals("SUCCESS", saved.getStatus());
        assertEquals("automated", saved.getSource());
        assertEquals("main", saved.getBranch());
        assertEquals("https://github.com/org/repo", saved.getRepoUrl());
        assertEquals("Plugin deployed successfully", saved.getMessage());
    }

    @Test
    @DisplayName("Should append to existing deployment history")
    void shouldAppendToExistingHistory() {
        service.recordDeployment("NewPlugin.jar", "SUCCESS", "webapp",
                null, null, "New deployment");

        verify(repository).save(any(DeploymentRecord.class));
    }

    @Test
    @DisplayName("Should return deployment history sorted by timestamp descending")
    void shouldReturnHistorySortedByTimestampDescending() {
        Instant oldest = Instant.now().minusSeconds(3600);
        Instant middle = Instant.now().minusSeconds(1800);
        Instant newest = Instant.now();

        // Return pre-sorted list (the repository query returns sorted results)
        List<DeploymentRecord> records = new ArrayList<>();
        records.add(new DeploymentRecord(newest, "NewPlugin.jar", "SUCCESS",
                "automated", null, null, null));
        records.add(new DeploymentRecord(middle, "MidPlugin.jar", "FAILURE",
                "webapp", null, null, null));
        records.add(new DeploymentRecord(oldest, "OldPlugin.jar", "SUCCESS",
                "automated", null, null, null));

        when(repository.findByTimestampAfterOrderByTimestampDesc(any(Instant.class)))
                .thenReturn(records);

        List<DeploymentRecord> history = service.getDeploymentHistory();

        assertEquals(3, history.size());
        assertEquals("NewPlugin.jar", history.get(0).getPluginName());
        assertEquals("MidPlugin.jar", history.get(1).getPluginName());
        assertEquals("OldPlugin.jar", history.get(2).getPluginName());
    }

    @Test
    @DisplayName("Should return empty list when no deployments exist")
    void shouldReturnEmptyListWhenNoDeploymentsExist() {
        when(repository.findByTimestampAfterOrderByTimestampDesc(any(Instant.class)))
                .thenReturn(new ArrayList<>());

        List<DeploymentRecord> history = service.getDeploymentHistory();

        assertNotNull(history);
        assertTrue(history.isEmpty());
    }
}
