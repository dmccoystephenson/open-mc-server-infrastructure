package com.openmc.minecraftwrapper.controller;

import com.openmc.minecraftwrapper.service.AlertService;
import com.openmc.minecraftwrapper.service.PluginDeployService;
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
@RequestMapping("/api/plugins")
public class PluginDeployController {

    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${deploy.auth.token:}")
    private String deployAuthToken;

    private final PluginDeployService pluginDeployService;
    private final AlertService alertService;

    public PluginDeployController(PluginDeployService pluginDeployService, AlertService alertService) {
        this.pluginDeployService = pluginDeployService;
        this.alertService = alertService;
    }

    /**
     * Deploy a plugin JAR with the uploaded file.
     *
     * <p>Authentication is required via an {@code Authorization: Bearer <token>} header.
     * The token must match the value configured in {@code deploy.auth.token}.
     *
     * @param authHeader the HTTP {@code Authorization} header
     * @param pluginName the filename of the plugin JAR to deploy (e.g. {@code MyPlugin.jar})
     * @param branch     optional Git branch name that triggered the deployment
     * @param repoUrl    optional URL of the repository that initiated the deployment
     * @param file       the new JAR file
     * @return 200 OK on success, 400 on bad input, 401 on auth failure, 500 on I/O error
     */
    @PostMapping("/deploy")
    public ResponseEntity<String> deploy(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam("pluginName") String pluginName,
            @RequestParam(value = "branch", required = false) String branch,
            @RequestParam(value = "repoUrl", required = false) String repoUrl,
            @RequestParam("file") MultipartFile file) {

        if (!isAuthorized(authHeader)) {
            log.warn("Unauthorized plugin deploy attempt");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        log.info("Received plugin deploy request");

        try {
            pluginDeployService.replacePlugin(pluginName, file);
            alertService.sendPluginDeploySuccessAlert(pluginName, branch, repoUrl);
            return ResponseEntity.ok("Plugin deployed successfully");
        } catch (IllegalArgumentException e) {
            log.warn("Invalid plugin deploy request: {}", e.getMessage());
            alertService.sendPluginDeployFailureAlert(pluginName, e.getMessage(), branch, repoUrl);
            return ResponseEntity.badRequest().body("Invalid request");
        } catch (IOException e) {
            log.error("Failed to deploy plugin: {}", e.getMessage());
            alertService.sendPluginDeployFailureAlert(pluginName, e.getMessage(), branch, repoUrl);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to deploy plugin");
        }
    }

    /**
     * Returns {@code true} if the provided {@code Authorization} header carries a Bearer
     * token that matches the configured deploy token.
     *
     * <p>Uses a constant-time comparison to prevent timing attacks.
     * Returns {@code false} when no token is configured to ensure the endpoint is always
     * protected.
     */
    private boolean isAuthorized(String authHeader) {
        if (deployAuthToken == null || deployAuthToken.trim().isEmpty()) {
            log.warn("deploy.auth.token is not configured; all deploy requests will be rejected");
            return false;
        }
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String providedToken = authHeader.substring(BEARER_PREFIX.length());
        return MessageDigest.isEqual(
                deployAuthToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8));
    }
}
