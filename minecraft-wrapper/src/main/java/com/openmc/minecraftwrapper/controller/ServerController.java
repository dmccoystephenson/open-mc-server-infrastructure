package com.openmc.minecraftwrapper.controller;

import com.openmc.minecraftwrapper.model.ServerMetrics;
import com.openmc.minecraftwrapper.model.ServerStatus;
import com.openmc.minecraftwrapper.service.MinecraftServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/server")
public class ServerController {

    private final MinecraftServerService minecraftServerService;

    @Value("${logs.diagnostic.enabled:false}")
    private boolean logsDiagnosticEnabled;

    @Value("${logs.diagnostic.max-lines:100}")
    private int logsDiagnosticMaxLines;

    public ServerController(MinecraftServerService minecraftServerService) {
        this.minecraftServerService = minecraftServerService;
    }

    @GetMapping("/status")
    public ResponseEntity<ServerStatus> getStatus() {
        log.info("Received request for server status");
        ServerStatus status = minecraftServerService.getStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Start the Minecraft server.
     * Returns 409 Conflict if the server is already running.
     * Returns 202 Accepted and starts the server asynchronously otherwise.
     */
    @PostMapping("/start")
    public ResponseEntity<String> start() {
        log.info("Received start request");

        if (minecraftServerService.getStatus().isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Server is already running");
        }

        CompletableFuture.runAsync(() -> {
            try {
                minecraftServerService.start();
            } catch (IllegalStateException e) {
                log.warn("Start rejected: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Failed to start server", e);
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Server start initiated");
    }

    /**
     * Stop the Minecraft server.
     * Returns 409 Conflict if the server is not running.
     * Returns 202 Accepted and stops the server asynchronously otherwise.
     */
    @PostMapping("/stop")
    public ResponseEntity<String> stop() {
        log.info("Received stop request");

        if (!minecraftServerService.getStatus().isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Server is not running");
        }

        CompletableFuture.runAsync(() -> {
            try {
                minecraftServerService.stop();
            } catch (IllegalStateException e) {
                log.warn("Stop rejected: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Failed to stop server", e);
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Server stop initiated");
    }

    /**
     * Restart the Minecraft server.
     * Returns 202 Accepted immediately and restarts the server asynchronously.
     */
    @PostMapping("/restart")
    public ResponseEntity<String> restart() {
        log.info("Received restart request");

        CompletableFuture.runAsync(() -> {
            try {
                minecraftServerService.restart();
            } catch (Exception e) {
                log.error("Failed to restart server", e);
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Server restart initiated");
    }

    @PostMapping("/command")
    public ResponseEntity<String> sendCommand(@RequestBody String command) {
        log.info("Received command request: {}", command);
        
        // Basic validation - only allow safe commands or require authentication in production
        // This is a placeholder for proper authentication/authorization
        if (command == null || command.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Command cannot be empty");
        }
        
        try {
            minecraftServerService.sendCommand(command);
            return ResponseEntity.ok("Command sent successfully");
        } catch (IllegalStateException e) {
            log.error("Server not running", e);
            return ResponseEntity.badRequest().body("Server is not running");
        } catch (IOException e) {
            log.error("Failed to send command", e);
            return ResponseEntity.internalServerError().body("Failed to send command");
        }
    }

    /**
     * Initiate graceful server shutdown.
     * Returns 202 Accepted immediately and performs shutdown asynchronously.
     * The shutdown process takes 30+ seconds to complete with player warnings.
     * Consider implementing proper authentication/authorization before exposing this endpoint.
     */
    @PostMapping("/shutdown")
    public ResponseEntity<String> shutdown() {
        log.info("Received shutdown request");
        
        CompletableFuture.runAsync(() -> {
            try {
                minecraftServerService.shutdown();
            } catch (Exception e) {
                log.error("Failed to shutdown server", e);
            }
        });
        
        // Return immediately with 202 Accepted
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Shutdown initiated - server will shut down gracefully in 30+ seconds");
    }

    /**
     * Return the last {@code lines} lines of the server log ({@code logs/latest.log}).
     * Disabled by default; set {@code logs.diagnostic.enabled=true} to enable.
     * GET /api/server/logs?lines=N
     */
    @GetMapping("/logs")
    public ResponseEntity<?> getLogs(@RequestParam(defaultValue = "100") int lines) {
        if (!logsDiagnosticEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Server log access is disabled. Set logs.diagnostic.enabled=true to enable.");
        }
        int clampedLines = Math.min(Math.max(1, lines), logsDiagnosticMaxLines);
        List<String> logLines = minecraftServerService.getRecentLogLines(clampedLines);
        log.info("Returning {} server log lines for diagnostics", logLines.size());
        return ResponseEntity.ok(Map.of("lines", logLines, "count", logLines.size()));
    }

    /**
     * Return a performance metrics snapshot (heap, server process memory, uptime, TPS).
     * GET /api/server/metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<ServerMetrics> getMetrics() {
        log.info("Received request for server metrics");
        ServerMetrics metrics = minecraftServerService.getServerMetrics();
        return ResponseEntity.ok(metrics);
    }
}
