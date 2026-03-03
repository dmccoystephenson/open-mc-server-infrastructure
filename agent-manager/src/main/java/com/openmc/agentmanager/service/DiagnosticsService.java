package com.openmc.agentmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that gathers diagnostic context from multiple infrastructure sources
 * (server status, recent alerts, latest backup) and returns a structured JSON blob
 * for Claude to synthesize into a natural language summary.
 *
 * <p>Partial upstream failures are handled gracefully: unavailable sources are
 * noted in an {@code unavailableSources} field so that Claude can reflect the gap
 * in its response.
 */
@Slf4j
@Service
public class DiagnosticsService {

    private final MinecraftWrapperService minecraftWrapperService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final LogSanitizerService logSanitizerService;

    @Value("${alert.manager.url:http://alert-manager:8090/api/alerts}")
    private String alertManagerAlertsUrl;

    @Value("${backup.manager.url:http://backup-manager:8091}")
    private String backupManagerUrl;

    @Value("${diagnostics.logs.enabled:false}")
    private boolean logsEnabled;

    @Value("${diagnostics.logs.max-lines:500}")
    private int logsMaxLines;

    @Value("${diagnostics.logs.anonymize:true}")
    private boolean logsAnonymize;

    @Value("${diagnostics.webapp.enabled:false}")
    private boolean webappEnabled;

    @Value("${webapp.url:http://webapp:8080}")
    private String webappUrl;

    public DiagnosticsService(MinecraftWrapperService minecraftWrapperService,
                              RestTemplate restTemplate,
                              ObjectMapper objectMapper,
                              LogSanitizerService logSanitizerService) {
        this.minecraftWrapperService = minecraftWrapperService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.logSanitizerService = logSanitizerService;
    }

    /**
     * Fetches activity tracker statistics from the webapp.
     *
     * @return JSON string containing activity tracker stats
     */
    public String getActivityTrackerStats() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(webappUrl + "/api/activity-tracker/stats", String.class);
            String body = response.getBody();
            return body != null ? body : "{}";
        } catch (Exception e) {
            log.error("Failed to fetch activity tracker stats from webapp: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch activity tracker stats: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches the ranked player leaderboard from the webapp's Activity Tracker.
     * Each entry includes player name, hours played, and total login count.
     *
     * @return JSON string containing the leaderboard array
     */
    public String getActivityTrackerLeaderboard() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(webappUrl + "/api/activity-tracker/leaderboard", String.class);
            String body = response.getBody();
            return body != null ? body : "[]";
        } catch (Exception e) {
            log.error("Failed to fetch activity tracker leaderboard from webapp: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch activity tracker leaderboard: " + e.getMessage(), e);
        }
    }

    /**
     * Collect diagnostic context from all infrastructure sources.
     *
     * @param limit maximum number of recent alerts to include (null defaults to 10)
     * @return a JSON string containing server status, recent alerts, and latest backup data
     */
    public String getServerDiagnostics(Integer limit) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        List<String> unavailableSources = new ArrayList<>();

        // 1. Server status from minecraft-wrapper
        try {
            String statusJson = minecraftWrapperService.getServerStatus();
            Object statusObj = objectMapper.readValue(statusJson, Object.class);
            diagnostics.put("serverStatus", statusObj);
        } catch (Exception e) {
            log.warn("Failed to fetch server status for diagnostics: {}", e.getMessage());
            diagnostics.put("serverStatus", null);
            unavailableSources.add("minecraft-wrapper");
        }

        // 2. Recent alerts from alert-manager
        try {
            int alertLimit = (limit == null) ? 10 : Math.min(100, Math.max(1, limit));
            String alertsUrl = alertManagerAlertsUrl + "?limit=" + alertLimit;
            ResponseEntity<String> alertsResponse = restTemplate.getForEntity(alertsUrl, String.class);
            if (alertsResponse.getBody() != null) {
                Object alertsObj = objectMapper.readValue(alertsResponse.getBody(), Object.class);
                diagnostics.put("recentAlerts", alertsObj);
            } else {
                diagnostics.put("recentAlerts", List.of());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch recent alerts for diagnostics: {}", e.getMessage());
            diagnostics.put("recentAlerts", null);
            unavailableSources.add("alert-manager");
        }

        // 3. Latest backup status from backup-manager
        try {
            String backupUrl = backupManagerUrl + "/api/backups/latest";
            ResponseEntity<String> backupResponse = restTemplate.getForEntity(backupUrl, String.class);
            if (backupResponse.getBody() != null) {
                Object backupObj = objectMapper.readValue(backupResponse.getBody(), Object.class);
                diagnostics.put("latestBackup", backupObj);
            } else {
                diagnostics.put("latestBackup", null);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch latest backup status for diagnostics: {}", e.getMessage());
            diagnostics.put("latestBackup", null);
            unavailableSources.add("backup-manager");
        }

        // 4. Server logs from minecraft-wrapper (optional, requires diagnostics.logs.enabled=true)
        if (logsEnabled) {
            try {
                String logsJson = minecraftWrapperService.getServerLogs(logsMaxLines);
                Map<?, ?> logsMap = objectMapper.readValue(logsJson, Map.class);
                Object rawLines = logsMap.get("lines");
                if (rawLines instanceof List<?> linesList) {
                    List<String> processedLines = linesList.stream()
                            .filter(l -> l != null)
                            .map(l -> logsAnonymize
                                    ? logSanitizerService.sanitize(l.toString())
                                    : l.toString())
                            .collect(Collectors.toList());
                    diagnostics.put("serverLogs", Map.of("lines", processedLines, "count", processedLines.size()));
                } else {
                    diagnostics.put("serverLogs", Map.of("lines", List.of(), "count", 0));
                }
            } catch (Exception e) {
                log.warn("Failed to fetch server logs for diagnostics: {}", e.getMessage());
                diagnostics.put("serverLogs", null);
                unavailableSources.add("server-logs");
            }
        }

        // 5. Server performance metrics (heap, TPS, process memory, uptime) from minecraft-wrapper
        try {
            String metricsJson = minecraftWrapperService.getServerMetrics();
            Object metricsObj = objectMapper.readValue(metricsJson, Object.class);
            diagnostics.put("serverMetrics", metricsObj);
        } catch (Exception e) {
            log.warn("Failed to fetch server metrics for diagnostics: {}", e.getMessage());
            diagnostics.put("serverMetrics", null);
            unavailableSources.add("server-metrics");
        }

        // 6. Webapp data (player list + activity tracker stats) — optional, requires diagnostics.webapp.enabled=true
        if (webappEnabled) {
            Map<String, Object> webappData = new LinkedHashMap<>();
            try {
                ResponseEntity<String> statusResponse = restTemplate.getForEntity(webappUrl + "/api/status", String.class);
                if (statusResponse.getBody() != null) {
                    webappData.put("serverStatus", objectMapper.readValue(statusResponse.getBody(), Object.class));
                }
            } catch (Exception e) {
                log.warn("Failed to fetch webapp server status for diagnostics: {}", e.getMessage());
                webappData.put("serverStatus", null);
            }
            try {
                ResponseEntity<String> statsResponse = restTemplate.getForEntity(webappUrl + "/api/activity-tracker/stats", String.class);
                if (statsResponse.getBody() != null) {
                    webappData.put("activityTrackerStats", objectMapper.readValue(statsResponse.getBody(), Object.class));
                }
            } catch (Exception e) {
                log.warn("Failed to fetch webapp activity-tracker stats for diagnostics: {}", e.getMessage());
                webappData.put("activityTrackerStats", null);
            }
            if (webappData.get("serverStatus") == null && webappData.get("activityTrackerStats") == null) {
                unavailableSources.add("webapp");
            } else {
                diagnostics.put("webappData", webappData);
            }
        }

        if (!unavailableSources.isEmpty()) {
            diagnostics.put("unavailableSources", unavailableSources);
        }

        try {
            return objectMapper.writeValueAsString(diagnostics);
        } catch (Exception e) {
            log.error("Failed to serialize diagnostics response", e);
            return "{\"error\":\"Failed to collect diagnostics\"}";
        }
    }
}
