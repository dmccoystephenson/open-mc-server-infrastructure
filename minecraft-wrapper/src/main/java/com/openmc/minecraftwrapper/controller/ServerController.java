package com.openmc.minecraftwrapper.controller;

import com.openmc.minecraftwrapper.model.ServerStatus;
import com.openmc.minecraftwrapper.service.MinecraftServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

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

    @PostMapping("/shutdown")
    public ResponseEntity<String> shutdown() {
        log.info("Received shutdown request");
        try {
            minecraftServerService.shutdown();
            return ResponseEntity.ok("Shutdown initiated");
        } catch (Exception e) {
            log.error("Failed to shutdown server", e);
            return ResponseEntity.internalServerError().body("Failed to shutdown server");
        }
    }
}
