package com.openmc.upgrademanager.controller;

import com.openmc.upgrademanager.service.MinecraftVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for version checking operations
 */
@RestController
@RequestMapping("/api/version")
@Slf4j
public class VersionCheckController {

    private final MinecraftVersionService versionService;

    public VersionCheckController(MinecraftVersionService versionService) {
        this.versionService = versionService;
    }

    /**
     * Get current Minecraft version
     * GET /api/version/current
     * 
     * @return Response with current version
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentVersion() {
        log.info("Current version check requested via API");
        Map<String, Object> response = new HashMap<>();
        
        String currentVersion = versionService.getCurrentVersion();
        
        response.put("version", currentVersion);
        response.put("success", !currentVersion.equals("unknown"));
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get latest Minecraft version
     * GET /api/version/latest
     * 
     * @return Response with latest version
     */
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestVersion() {
        log.info("Latest version check requested via API");
        Map<String, Object> response = new HashMap<>();
        
        String latestVersion = versionService.getLatestMinecraftVersion();
        
        response.put("version", latestVersion);
        response.put("success", latestVersion != null);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Check if server is outdated
     * GET /api/version/check
     * 
     * @return Response with version comparison
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkVersion() {
        log.info("Version check requested via API");
        Map<String, Object> response = new HashMap<>();
        
        String currentVersion = versionService.getCurrentVersion();
        String latestVersion = versionService.getLatestMinecraftVersion();
        
        response.put("currentVersion", currentVersion);
        response.put("latestVersion", latestVersion);
        
        if (currentVersion.equals("unknown") || latestVersion == null) {
            response.put("outdated", false);
            response.put("message", "Unable to determine version status");
        } else if (currentVersion.equals(latestVersion)) {
            response.put("outdated", false);
            response.put("message", "Server is up to date");
        } else {
            response.put("outdated", true);
            response.put("message", "Server is outdated");
        }
        
        response.put("success", true);
        
        return ResponseEntity.ok(response);
    }
}
