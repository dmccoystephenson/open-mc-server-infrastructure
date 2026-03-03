package com.openmc.agentmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
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

    private DiagnosticsService diagnosticsService;

    @BeforeEach
    void setUp() {
        diagnosticsService = new DiagnosticsService(minecraftWrapperService, restTemplate, new ObjectMapper(), new LogSanitizerService());
        ReflectionTestUtils.setField(diagnosticsService, "alertManagerAlertsUrl", "http://test:8090/api/alerts");
        ReflectionTestUtils.setField(diagnosticsService, "backupManagerUrl", "http://test:8091");
        // default stub so existing tests don't fail on the new metrics call
        lenient().when(minecraftWrapperService.getServerMetrics())
                .thenReturn("{\"wrapperHeapUsedMb\":50,\"wrapperHeapMaxMb\":256,\"wrapperHeapUsedPercent\":19.5}");
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

    @Test
    @DisplayName("Should not include serverLogs when logs are disabled (default)")
    void shouldNotIncludeServerLogsWhenDisabled() throws Exception {
        // logsEnabled defaults to false — no log fetch should occur
        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));

        String result = diagnosticsService.getServerDiagnostics(null);

        assertFalse(result.contains("serverLogs"));
        verify(minecraftWrapperService, never()).getServerLogs(anyInt());
    }

    @Test
    @DisplayName("Should include sanitized serverLogs when logs are enabled")
    void shouldIncludeSanitizedServerLogsWhenEnabled() throws Exception {
        ReflectionTestUtils.setField(diagnosticsService, "logsEnabled", true);
        ReflectionTestUtils.setField(diagnosticsService, "logsMaxLines", 50);
        // logsAnonymize defaults to false in the test-constructed service; set it explicitly
        ReflectionTestUtils.setField(diagnosticsService, "logsAnonymize", true);

        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));
        when(minecraftWrapperService.getServerLogs(50))
                .thenReturn("{\"lines\":[\"[INFO]: Steve[/192.168.1.1:12345] logged in\"],\"count\":1}");

        String result = diagnosticsService.getServerDiagnostics(null);

        assertTrue(result.contains("serverLogs"));
        assertFalse(result.contains("192.168.1.1"), "IP address should be redacted");
        assertTrue(result.contains("IP_REDACTED"));
    }

    @Test
    @DisplayName("Should not redact IPs in serverLogs when anonymization is disabled")
    void shouldNotRedactIpsWhenAnonymizationDisabled() throws Exception {
        ReflectionTestUtils.setField(diagnosticsService, "logsEnabled", true);
        ReflectionTestUtils.setField(diagnosticsService, "logsMaxLines", 50);
        ReflectionTestUtils.setField(diagnosticsService, "logsAnonymize", false);

        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));
        when(minecraftWrapperService.getServerLogs(50))
                .thenReturn("{\"lines\":[\"[INFO]: Steve[/192.168.1.1:12345] logged in\"],\"count\":1}");

        String result = diagnosticsService.getServerDiagnostics(null);

        assertTrue(result.contains("serverLogs"));
        assertTrue(result.contains("192.168.1.1"), "IP address should NOT be redacted when anonymization is off");
        assertFalse(result.contains("IP_REDACTED"));
    }

    @Test
    @DisplayName("Should add server-logs to unavailableSources when log fetch fails")
    void shouldAddServerLogsToUnavailableSourcesWhenFetchFails() throws Exception {
        ReflectionTestUtils.setField(diagnosticsService, "logsEnabled", true);
        ReflectionTestUtils.setField(diagnosticsService, "logsMaxLines", 50);

        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));
        when(minecraftWrapperService.getServerLogs(50))
                .thenThrow(new RuntimeException("403 Forbidden"));

        String result = diagnosticsService.getServerDiagnostics(null);

        assertTrue(result.contains("unavailableSources"));
        assertTrue(result.contains("server-logs"));
    }

    @Test
    @DisplayName("Should include serverMetrics from minecraft-wrapper")
    void shouldIncludeServerMetrics() throws Exception {
        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));
        when(minecraftWrapperService.getServerMetrics())
                .thenReturn("{\"wrapperHeapUsedMb\":80,\"wrapperHeapMaxMb\":256,\"wrapperHeapUsedPercent\":31.2," +
                        "\"tps\":\"TPS from last 1m, 5m, 15m: 19.99, 19.96, 19.94\"}");

        String result = diagnosticsService.getServerDiagnostics(null);

        assertTrue(result.contains("serverMetrics"));
        assertTrue(result.contains("wrapperHeapUsedMb"));
        assertTrue(result.contains("tps"));
        assertFalse(result.contains("unavailableSources"));
    }

    @Test
    @DisplayName("Should add server-metrics to unavailableSources when metrics fetch fails")
    void shouldAddServerMetricsToUnavailableSourcesWhenFetchFails() throws Exception {
        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));
        when(minecraftWrapperService.getServerMetrics())
                .thenThrow(new RuntimeException("Connection refused"));

        String result = diagnosticsService.getServerDiagnostics(null);

        assertTrue(result.contains("unavailableSources"));
        assertTrue(result.contains("server-metrics"));
    }

    @Test
    @DisplayName("Should not include webappData when webapp is disabled (default)")
    void shouldNotIncludeWebappDataWhenDisabled() throws Exception {
        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));

        String result = diagnosticsService.getServerDiagnostics(null);

        assertFalse(result.contains("webappData"));
    }

    @Test
    @DisplayName("Should include webappData when webapp is enabled")
    void shouldIncludeWebappDataWhenEnabled() throws Exception {
        ReflectionTestUtils.setField(diagnosticsService, "webappEnabled", true);
        ReflectionTestUtils.setField(diagnosticsService, "webappUrl", "http://test-webapp:8080");

        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test-webapp:8080/api/status"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"online\":true,\"maxPlayers\":20}", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test-webapp:8080/api/activity-tracker/stats"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"uniqueLogins\":5,\"totalLogins\":12}", HttpStatus.OK));

        String result = diagnosticsService.getServerDiagnostics(null);

        assertTrue(result.contains("webappData"));
        assertTrue(result.contains("uniqueLogins"));
        assertFalse(result.contains("unavailableSources"));
    }

    @Test
    @DisplayName("Should add webapp to unavailableSources when both webapp calls fail")
    void shouldAddWebappToUnavailableSourcesWhenBothCallsFail() throws Exception {
        ReflectionTestUtils.setField(diagnosticsService, "webappEnabled", true);
        ReflectionTestUtils.setField(diagnosticsService, "webappUrl", "http://test-webapp:8080");

        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test-webapp:8080/api/status"), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));
        when(restTemplate.getForEntity(eq("http://test-webapp:8080/api/activity-tracker/stats"), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        String result = diagnosticsService.getServerDiagnostics(null);

        assertTrue(result.contains("unavailableSources"));
        assertTrue(result.contains("webapp"));
        assertFalse(result.contains("webappData"));
    }

    @Test
    @DisplayName("Should include webappData with partial data when only one webapp endpoint fails")
    void shouldIncludeWebappDataWhenOnlyOneEndpointFails() throws Exception {
        ReflectionTestUtils.setField(diagnosticsService, "webappEnabled", true);
        ReflectionTestUtils.setField(diagnosticsService, "webappUrl", "http://test-webapp:8080");

        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");
        when(restTemplate.getForEntity(eq("http://test:8090/api/alerts?limit=10"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test:8091/api/backups/latest"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"available\":false}", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test-webapp:8080/api/status"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"online\":true,\"maxPlayers\":20}", HttpStatus.OK));
        when(restTemplate.getForEntity(eq("http://test-webapp:8080/api/activity-tracker/stats"), eq(String.class)))
                .thenThrow(new RuntimeException("Activity Tracker not enabled"));

        String result = diagnosticsService.getServerDiagnostics(null);

        assertTrue(result.contains("webappData"));
        assertFalse(result.contains("\"webapp\""), "webapp should not be in unavailableSources when status succeeded");
    }
}
