package com.openmc.webapp.service;

import com.openmc.webapp.model.DeploymentRecord;
import com.openmc.webapp.repository.DeploymentRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("DeploymentHistoryService Tests")
class DeploymentHistoryServiceTest {

    private DeploymentRecordRepository repository;
    private DeploymentHistoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(DeploymentRecordRepository.class);
        service = new DeploymentHistoryService(repository);
    }

    @Test
    @DisplayName("Should record a new deployment")
    void shouldRecordNewDeployment() {
        when(repository.findAll()).thenReturn(new ArrayList<>());

        service.recordDeployment("MyPlugin.jar", "SUCCESS", "automated",
                "main", "https://github.com/org/repo", "Plugin deployed successfully");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DeploymentRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(captor.capture());

        List<DeploymentRecord> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals("MyPlugin.jar", saved.get(0).getPluginName());
        assertEquals("SUCCESS", saved.get(0).getStatus());
        assertEquals("automated", saved.get(0).getSource());
        assertEquals("main", saved.get(0).getBranch());
        assertEquals("https://github.com/org/repo", saved.get(0).getRepoUrl());
        assertEquals("Plugin deployed successfully", saved.get(0).getMessage());
    }

    @Test
    @DisplayName("Should append to existing deployment history")
    void shouldAppendToExistingHistory() {
        List<DeploymentRecord> existing = new ArrayList<>();
        existing.add(new DeploymentRecord(Instant.now(), "OldPlugin.jar", "SUCCESS",
                "automated", "main", null, "Old deployment"));
        when(repository.findAll()).thenReturn(existing);

        service.recordDeployment("NewPlugin.jar", "SUCCESS", "webapp",
                null, null, "New deployment");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DeploymentRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).save(captor.capture());

        List<DeploymentRecord> saved = captor.getValue();
        assertEquals(2, saved.size());
    }

    @Test
    @DisplayName("Should return deployment history sorted by timestamp descending")
    void shouldReturnHistorySortedByTimestampDescending() {
        List<DeploymentRecord> records = new ArrayList<>();
        Instant oldest = Instant.now().minusSeconds(3600);
        Instant middle = Instant.now().minusSeconds(1800);
        Instant newest = Instant.now();

        records.add(new DeploymentRecord(oldest, "OldPlugin.jar", "SUCCESS",
                "automated", null, null, null));
        records.add(new DeploymentRecord(newest, "NewPlugin.jar", "SUCCESS",
                "automated", null, null, null));
        records.add(new DeploymentRecord(middle, "MidPlugin.jar", "FAILURE",
                "webapp", null, null, null));

        when(repository.findAll()).thenReturn(records);

        List<DeploymentRecord> history = service.getDeploymentHistory();

        assertEquals(3, history.size());
        assertEquals("NewPlugin.jar", history.get(0).getPluginName());
        assertEquals("MidPlugin.jar", history.get(1).getPluginName());
        assertEquals("OldPlugin.jar", history.get(2).getPluginName());
    }

    @Test
    @DisplayName("Should return empty list when no deployments exist")
    void shouldReturnEmptyListWhenNoDeploymentsExist() {
        when(repository.findAll()).thenReturn(new ArrayList<>());

        List<DeploymentRecord> history = service.getDeploymentHistory();

        assertNotNull(history);
        assertTrue(history.isEmpty());
    }
}
