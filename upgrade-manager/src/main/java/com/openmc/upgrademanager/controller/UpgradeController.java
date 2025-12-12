package com.openmc.upgrademanager.controller;

import com.openmc.upgrademanager.exception.UpgradeException;
import com.openmc.upgrademanager.model.UpgradeResult;
import com.openmc.upgrademanager.service.UpgradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for upgrade operations
 */
@RestController
@RequestMapping("/api/upgrade")
@Slf4j
public class UpgradeController {

    private final UpgradeService upgradeService;

    public UpgradeController(UpgradeService upgradeService) {
        this.upgradeService = upgradeService;
    }

    /**
     * Trigger a server upgrade
     * POST /api/upgrade/trigger
     * 
     * @param request Map containing the new version
     * @return Response with upgrade status
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerUpgrade(@RequestBody Map<String, String> request) {
        String newVersion = request.get("version");
        
        if (newVersion == null || newVersion.trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Version parameter is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        log.info("Upgrade triggered via API for version: {}", newVersion);
        Map<String, Object> response = new HashMap<>();
        
        try {
            UpgradeResult result = upgradeService.performUpgrade(newVersion);
            
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("previousVersion", result.getPreviousVersion());
            response.put("newVersion", result.getNewVersion());
            response.put("backupPath", result.getBackupPath());
            
            log.info("Upgrade completed successfully to version {}", newVersion);
            return ResponseEntity.ok(response);
            
        } catch (UpgradeException e) {
            log.error("Upgrade failed", e);
            response.put("success", false);
            response.put("message", "Upgrade failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
