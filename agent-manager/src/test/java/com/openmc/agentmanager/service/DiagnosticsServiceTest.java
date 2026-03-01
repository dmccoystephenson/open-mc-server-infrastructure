package com.openmc.agentmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiagnosticsService Tests")
class DiagnosticsServiceTest {

    @Mock
    private MinecraftWrapperService minecraftWrapperService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private DiagnosticsService diagnosticsService;

    @BeforeEach
    void setUp() {
        // Use real ObjectMapper for JSON processing
        ReflectionTestUtils.setField(diagnosticsService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(diagnosticsService, "alertManagerAlertsUrl", "http://test:8090/api/alerts");
        ReflectionTestUtils.setField(diagnosticsService, "backupManagerUrl", "http://test:8091");
    }

    @Test
    @DisplayName("Should return combined diagnostics from all sources")
    void shouldReturnCombinedDiagnosticsFromAllSources() throws Exception {
        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true,\"players\":3}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[{\"title\":\"Backup Completed\",\"level\":\"INFO\"}]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":true,\"success\":true}", HttpStatus.OK));

        String result = diagnosticsService.getServerDiagnostics(null);

        assertNotNull(result);
        assertTrue(result.contains("serverStatus"));
        assertTrue(result.contains("recentAlerts"));
        assertTrue(result.contains("latestBackup"));
        assertFalse(result.contains("unavailableSources"));
    }

    @Test
    @DisplayName("Should include unavailableSources when server status fails")
    void shouldIncludeUnavailableSourcesWhenServerStatusFails() throws Exception {
        when(minecraftWrapperService.getServerStatus()).thenThrow(new RuntimeException("Connection refused"));
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));

        String result = diagnosticsService.getServerDiagnostics(null);

        assertTrue(result.contains("unavailableSources"));
        assertTrue(result.contains("minecraft-wrapper"));
    }

    @Test
    @DisplayName("Should include unavailableSources when alert-manager is unavailable")
    void shouldIncludeUnavailableSourcesWhenAlertManagerUnavailable() throws Exception {
        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));

        String result = diagnosticsService.getServerDiagnostics(null);

        assertTrue(result.contains("unavailableSources"));
        assertTrue(result.contains("alert-manager"));
    }

    @Test
    @DisplayName("Should include unavailableSources when backup-manager is unavailable")
    void shouldIncludeUnavailableSourcesWhenBackupManagerUnavailable() throws Exception {
        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        String result = diagnosticsService.getServerDiagnostics(null);

        assertTrue(result.contains("unavailableSources"));
        assertTrue(result.contains("backup-manager"));
    }

    @Test
    @DisplayName("Should still return partial data when all sources fail")
    void shouldReturnPartialDataWhenAllSourcesFail() throws Exception {
        when(minecraftWrapperService.getServerStatus()).thenThrow(new RuntimeException("Unavailable"));
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Unavailable"));

        String result = diagnosticsService.getServerDiagnostics(null);

        assertNotNull(result);
        assertTrue(result.contains("unavailableSources"));
        // Should not throw — always returns valid JSON
        assertDoesNotThrow(() -> new ObjectMapper().readValue(result, Object.class));
    }

    @Test
    @DisplayName("Should pass custom limit to alert-manager URL")
    void shouldPassCustomLimitToAlertManagerUrl() throws Exception {
        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=5"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));

        diagnosticsService.getServerDiagnostics(5);

        verify(restTemplate).getForEntity(eq("http://test:8090/api/alerts?limit=5"), eq(String.class));
    }
}
