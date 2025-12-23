package com.openmc.minecraftwrapper.controller;

import com.openmc.minecraftwrapper.model.ServerStatus;
import com.openmc.minecraftwrapper.service.MinecraftServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/server")
public class ServerController {

    private final MinecraftServerService minecraftServerService;

    public ServerController(MinecraftServerService minecraftServerService) {
        this.minecraftServerService = minecraftServerService;
    }

    @GetMapping("/status")
    public ResponseEntity<ServerStatus> getStatus() {
        log.info("Received request for server status");
        ServerStatus status = minecraftServerService.getStatus();
        return ResponseEntity.ok(status);
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
     * Note: This endpoint will block for 30+ seconds during graceful shutdown.
     * Consider implementing proper authentication/authorization before exposing this endpoint.
     */
    @PostMapping("/shutdown")
    public ResponseEntity<String> shutdown() {
        log.info("Received shutdown request");
        
        // Execute shutdown asynchronously to avoid blocking the HTTP request
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
}
