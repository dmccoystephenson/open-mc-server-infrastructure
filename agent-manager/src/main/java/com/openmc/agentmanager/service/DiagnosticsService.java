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

    @Value("${alert.manager.url:http://alert-manager:8090/api/alerts}")
    private String alertManagerAlertsUrl;

    @Value("${backup.manager.url:http://backup-manager:8091}")
    private String backupManagerUrl;

    public DiagnosticsService(MinecraftWrapperService minecraftWrapperService,
                              RestTemplate restTemplate,
                              ObjectMapper objectMapper) {
        this.minecraftWrapperService = minecraftWrapperService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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
