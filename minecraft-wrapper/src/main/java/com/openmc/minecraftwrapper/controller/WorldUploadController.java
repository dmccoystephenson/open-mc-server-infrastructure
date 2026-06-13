package com.openmc.minecraftwrapper.controller;

import com.openmc.minecraftwrapper.service.AlertService;
import com.openmc.minecraftwrapper.service.WorldUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@RestController
@RequestMapping("/api/world")
public class WorldUploadController {

    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${deploy.auth.token:}")
    private String deployAuthToken;

    private final WorldUploadService worldUploadService;
    private final AlertService alertService;

    public WorldUploadController(WorldUploadService worldUploadService, AlertService alertService) {
        this.worldUploadService = worldUploadService;
        this.alertService = alertService;
    }

    /**
     * Replace the server world with the uploaded ZIP archive.
     *
     * <p>Requires an {@code Authorization: Bearer <token>} header matching the configured
     * {@code deploy.auth.token}. The server is stopped before extraction and restarted
     * afterward. Accepts ZIP archives structured with the world folder at the top level
     * or with world contents at the root of the archive.
     *
     * @param authHeader the HTTP {@code Authorization} header
     * @param file       the ZIP archive containing the world
     * @return 200 OK on success, 400 on invalid input, 401 on auth failure, 500 on I/O error
     */
    @PostMapping("/upload")
    public ResponseEntity<String> upload(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam("file") MultipartFile file) {

        if (!isAuthorized(authHeader)) {
            log.warn("Unauthorized world upload attempt");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        log.info("Received world upload request");

        try {
            worldUploadService.replaceWorld(file);
            alertService.sendWorldUploadSuccessAlert();
            return ResponseEntity.ok("World uploaded successfully");
        } catch (IllegalArgumentException e) {
            log.warn("Invalid world upload request: {}", e.getMessage());
            alertService.sendWorldUploadFailureAlert(e.getMessage());
            return ResponseEntity.badRequest().body("Invalid request: " + e.getMessage());
        } catch (IOException e) {
            log.error("Failed to upload world: {}", e.getMessage());
            alertService.sendWorldUploadFailureAlert(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload world");
        }
    }

    private boolean isAuthorized(String authHeader) {
        if (deployAuthToken == null || deployAuthToken.trim().isEmpty()) {
            log.warn("deploy.auth.token is not configured; all world upload requests will be rejected");
            return false;
        }
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String provided = authHeader.substring(BEARER_PREFIX.length());
        return MessageDigest.isEqual(
                deployAuthToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
