package com.openmc.upgrademanager.controller;

import com.openmc.upgrademanager.exception.UpgradeException;
import com.openmc.upgrademanager.model.UpgradeRequest;
import com.openmc.upgrademanager.model.UpgradeResponse;
import com.openmc.upgrademanager.service.UpgradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for upgrade operations
 */
@RestController
@RequestMapping("/api/upgrades")
@Slf4j
public class UpgradeController {

    private final UpgradeService upgradeService;

    public UpgradeController(UpgradeService upgradeService) {
        this.upgradeService = upgradeService;
    }

    /**
     * Trigger a server upgrade
     * POST /api/upgrades/trigger
     * 
     * @param request The upgrade request containing the new version
     * @return Response with upgrade status and details
     */
    @PostMapping("/trigger")
    public ResponseEntity<UpgradeResponse> triggerUpgrade(@RequestBody UpgradeRequest request) {
        log.info("Upgrade triggered via API for version: {}", request.getNewVersion());
        
        if (request.getNewVersion() == null || request.getNewVersion().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    UpgradeResponse.builder()
                            .success(false)
                            .message("New version is required")
                            .error("Missing or empty version parameter")
                            .build()
            );
        }

        try {
            Map<String, Object> result = upgradeService.performUpgrade(request.getNewVersion());
            
            UpgradeResponse response = UpgradeResponse.builder()
                    .success(true)
                    .message((String) result.get("message"))
                    .previousVersion((String) result.get("previousVersion"))
                    .newVersion((String) result.get("newVersion"))
                    .backupPath((String) result.get("backupPath"))
                    .build();
            
            log.info("Upgrade completed successfully");
            return ResponseEntity.ok(response);
            
        } catch (UpgradeException e) {
            log.error("Upgrade failed", e);
            
            UpgradeResponse response = UpgradeResponse.builder()
                    .success(false)
                    .message("Upgrade failed")
                    .error(e.getMessage())
                    .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
