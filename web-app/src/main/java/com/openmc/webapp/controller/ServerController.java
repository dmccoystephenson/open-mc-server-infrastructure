package com.openmc.webapp.controller;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.dto.PluginDeleteRequest;
import com.openmc.webapp.dto.PluginListRequest;
import com.openmc.webapp.dto.PluginListResponse;
import com.openmc.webapp.dto.PluginOperationResponse;
import com.openmc.webapp.model.ActivityTrackerStats;
import com.openmc.webapp.model.LeaderboardEntry;
import com.openmc.webapp.service.ActivityTrackerService;
import com.openmc.webapp.service.AlertNotificationService;
import com.openmc.webapp.service.PluginService;
import com.openmc.webapp.service.RconService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
public class ServerController {
    
    private static final Logger logger = LoggerFactory.getLogger(ServerController.class);
    
    private final RconService rconService;
    private final ServerConfig serverConfig;
    private final ActivityTrackerService activityTrackerService;
    private final PluginService pluginService;
    private final AlertNotificationService alertNotificationService;
    private final com.openmc.webapp.service.MinecraftWrapperService minecraftWrapperService;
    
    public ServerController(RconService rconService, ServerConfig serverConfig, 
                          ActivityTrackerService activityTrackerService,
                          PluginService pluginService,
                          AlertNotificationService alertNotificationService,
                          com.openmc.webapp.service.MinecraftWrapperService minecraftWrapperService) {
        this.rconService = rconService;
        this.serverConfig = serverConfig;
        this.activityTrackerService = activityTrackerService;
        this.pluginService = pluginService;
        this.alertNotificationService = alertNotificationService;
        this.minecraftWrapperService = minecraftWrapperService;
    }
    
    @GetMapping("/")
    public String index() {
        return "redirect:/public";
    }
    
    @GetMapping("/public")
    public String publicPage(Model model) {
        RconService.ServerStatus status = rconService.getServerStatus();
        model.addAttribute("status", status);
        model.addAttribute("dynmapUrl", serverConfig.getDynmapUrl());
        model.addAttribute("bluemapUrl", serverConfig.getBluemapUrl());
        model.addAttribute("refreshIntervalMs", serverConfig.getRefreshIntervalMs());
        model.addAttribute("lastFetchTime", rconService.getLastFetchTime());
        model.addAttribute("activityTrackerEnabled", activityTrackerService.isEnabled());
        model.addAttribute("dashboardTitle", serverConfig.getDashboardTitle());
        model.addAttribute("dashboardSubtitle", serverConfig.getDashboardSubtitle());
        model.addAttribute("dashboardPrimaryColor", serverConfig.getDashboardPrimaryColor());
        model.addAttribute("dashboardSecondaryColor", serverConfig.getDashboardSecondaryColor());
        return "public";
    }
    
    @GetMapping("/admin")
    public String adminPage(Model model) {
        model.addAttribute("dashboardTitle", serverConfig.getDashboardTitle());
        model.addAttribute("dashboardSubtitle", serverConfig.getDashboardSubtitle());
        model.addAttribute("dashboardPrimaryColor", serverConfig.getDashboardPrimaryColor());
        model.addAttribute("dashboardSecondaryColor", serverConfig.getDashboardSecondaryColor());
        return "admin";
    }
    
    @PostMapping("/api/command")
    @ResponseBody
    public Map<String, String> sendCommand(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        String command = payload.get("command");
        
        // Validate credentials
        if (username == null || password == null) {
            alertNotificationService.sendWarningAlert(
                "Admin Authentication Failed",
                "Failed authentication attempt - missing credentials"
            );
            return Map.of("result", "Error: Username and password are required");
        }
        
        if (!validateCredentials(username, password)) {
            alertNotificationService.sendWarningAlert(
                "Admin Authentication Failed",
                "Failed authentication attempt for admin command endpoint"
            );
            return Map.of("result", "Error: Invalid username or password");
        }
        
        // Validate command
        if (command == null || command.trim().isEmpty()) {
            return Map.of("result", "Error: Command cannot be empty");
        }
        
        String result = rconService.sendCommand(command);
        
        // Send alert for successful command execution
        alertNotificationService.sendInfoAlert(
            "Server Command Executed",
            String.format("User '%s' executed command: %s", username, command)
        );
        
        return Map.of("result", result);
    }
    
    @GetMapping("/api/status")
    @ResponseBody
    public RconService.ServerStatus getStatus() {
        return rconService.getServerStatus();
    }
    
    @GetMapping("/api/resources")
    @ResponseBody
    public RconService.ResourceUsage getResources() {
        return rconService.getResourceUsage();
    }
    
    @GetMapping("/api/history")
    @ResponseBody
    public Map<String, Object> getHistory() {
        return Map.of("history", rconService.getRetrievalHistory());
    }
    
    @GetMapping("/api/history/max-size")
    @ResponseBody
    public Map<String, Integer> getHistoryMaxSize() {
        return Map.of("maxSize", rconService.getMaxHistorySize());
    }
    
    @PostMapping("/api/history/max-size")
    @ResponseBody
    public Map<String, Object> setHistoryMaxSize(@RequestBody Map<String, Integer> payload) {
        Integer maxSize = payload.get("maxSize");
        
        if (maxSize == null) {
            return Map.of("success", false, "error", "maxSize is required");
        }
        
        if (maxSize <= 0) {
            return Map.of("success", false, "error", "maxSize must be greater than 0");
        }
        
        try {
            rconService.setMaxHistorySize(maxSize);
            return Map.of("success", true, "maxSize", maxSize);
        } catch (IllegalArgumentException e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
    
    @GetMapping("/api/activity-tracker/stats")
    @ResponseBody
    public ActivityTrackerStats getActivityTrackerStats() {
        logger.debug("API request: /api/activity-tracker/stats");
        ActivityTrackerStats stats = activityTrackerService.getStats();
        if (stats == null) {
            logger.warn("Activity Tracker stats request returned null - check if integration is enabled and API is accessible");
        }
        return stats;
    }
    
    @GetMapping("/api/activity-tracker/leaderboard")
    @ResponseBody
    public List<LeaderboardEntry> getActivityTrackerLeaderboard() {
        logger.debug("API request: /api/activity-tracker/leaderboard");
        List<LeaderboardEntry> leaderboard = activityTrackerService.getLeaderboard();
        if (leaderboard.isEmpty()) {
            logger.warn("Activity Tracker leaderboard request returned empty - check if integration is enabled and API is accessible");
        }
        return leaderboard;
    }
    
    @GetMapping("/api/activity-tracker/enabled")
    @ResponseBody
    public Map<String, Boolean> getActivityTrackerEnabled() {
        boolean enabled = activityTrackerService.isEnabled();
        logger.debug("API request: /api/activity-tracker/enabled - returning: {}", enabled);
        return Map.of("enabled", enabled);
    }
    
    /**
     * Validates admin credentials using constant-time comparison to prevent timing attacks
     */
    private boolean validateCredentials(String username, String password) {
        String adminUsername = serverConfig.getAdminUsername();
        String adminPassword = serverConfig.getAdminPassword();
        
        // Check for null values
        if (username == null || password == null || adminUsername == null || adminPassword == null) {
            return false;
        }
        
        // Use constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(username.getBytes(), adminUsername.getBytes()) &&
               MessageDigest.isEqual(password.getBytes(), adminPassword.getBytes());
    }
    
    @PostMapping(value = "/api/plugins/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PluginListResponse listPlugins(@RequestBody PluginListRequest request) {
        // Validate credentials
        if (!validateCredentials(request.getUsername(), request.getPassword())) {
            alertNotificationService.sendWarningAlert(
                "Plugin List Authentication Failed",
                "Failed authentication attempt for plugin list endpoint"
            );
            return PluginListResponse.error("Invalid username or password");
        }
        
        List<String> plugins = pluginService.listPlugins();
        return PluginListResponse.success(plugins);
    }
    
    @PostMapping(value = "/api/plugins/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PluginOperationResponse uploadPlugin(@RequestParam(required = false) String username, 
                                                @RequestParam(required = false) String password,
                                                @RequestParam(value = "file", required = false) MultipartFile file) {
        // Validate required parameters
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return PluginOperationResponse.error("Missing username or password");
        }
        
        if (file == null || file.isEmpty()) {
            return PluginOperationResponse.error("No file provided");
        }
        
        // Validate credentials
        if (!validateCredentials(username, password)) {
            alertNotificationService.sendWarningAlert(
                "Plugin Upload Authentication Failed",
                "Failed authentication attempt for plugin upload endpoint"
            );
            return PluginOperationResponse.error("Invalid username or password");
        }
        
        String result = pluginService.uploadPlugin(file);
        boolean success = !result.startsWith("Error");
        
        if (success) {
            alertNotificationService.sendInfoAlert(
                "Plugin Uploaded Successfully",
                String.format("User '%s' uploaded plugin: %s", username, file.getOriginalFilename())
            );
            return PluginOperationResponse.success(result);
        } else {
            return PluginOperationResponse.error(result);
        }
    }
    
    @PostMapping(value = "/api/plugins/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PluginOperationResponse deletePlugin(@RequestBody PluginDeleteRequest request) {
        // Validate required parameters are present
        if (request.getUsername() == null || request.getUsername().isEmpty() || 
            request.getPassword() == null || request.getPassword().isEmpty() ||
            request.getFilename() == null || request.getFilename().isEmpty()) {
            return PluginOperationResponse.error("Missing required parameters");
        }
        
        // Validate credentials
        if (!validateCredentials(request.getUsername(), request.getPassword())) {
            alertNotificationService.sendWarningAlert(
                "Plugin Delete Authentication Failed",
                "Failed authentication attempt for plugin deletion endpoint"
            );
            return PluginOperationResponse.error("Invalid username or password");
        }
        
        String result = pluginService.deletePlugin(request.getFilename());
        boolean success = !result.startsWith("Error");
        
        if (success) {
            alertNotificationService.sendInfoAlert(
                "Plugin Deleted Successfully",
                String.format("User '%s' deleted plugin: %s", request.getUsername(), request.getFilename())
            );
            return PluginOperationResponse.success(result);
        } else {
            return PluginOperationResponse.error(result);
        }
    }
    
    @PostMapping("/api/server/start")
    @ResponseBody
    public Map<String, Object> startServer(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        
        // Validate credentials
        if (username == null || password == null || !validateCredentials(username, password)) {
            alertNotificationService.sendWarningAlert(
                "Server Start Authentication Failed",
                "Failed authentication attempt for server start endpoint"
            );
            return Map.of("success", false, "message", "Invalid username or password");
        }
        
        boolean success = minecraftWrapperService.startServer();
        
        if (success) {
            alertNotificationService.sendInfoAlert(
                "Server Start Initiated",
                String.format("User '%s' initiated server start", username)
            );
            return Map.of("success", true, "message", "Server start initiated");
        } else {
            return Map.of("success", false, "message", "Failed to start server");
        }
    }
    
    @PostMapping("/api/server/stop")
    @ResponseBody
    public Map<String, Object> stopServer(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        
        // Validate credentials
        if (username == null || password == null || !validateCredentials(username, password)) {
            alertNotificationService.sendWarningAlert(
                "Server Stop Authentication Failed",
                "Failed authentication attempt for server stop endpoint"
            );
            return Map.of("success", false, "message", "Invalid username or password");
        }
        
        boolean success = minecraftWrapperService.stopServer();
        
        if (success) {
            alertNotificationService.sendInfoAlert(
                "Server Stop Initiated",
                String.format("User '%s' initiated server stop", username)
            );
            return Map.of("success", true, "message", "Server stop initiated");
        } else {
            return Map.of("success", false, "message", "Failed to stop server");
        }
    }
    
    @PostMapping("/api/server/restart")
    @ResponseBody
    public Map<String, Object> restartServer(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        
        // Validate credentials
        if (username == null || password == null || !validateCredentials(username, password)) {
            alertNotificationService.sendWarningAlert(
                "Server Restart Authentication Failed",
                "Failed authentication attempt for server restart endpoint"
            );
            return Map.of("success", false, "message", "Invalid username or password");
        }
        
        boolean success = minecraftWrapperService.restartServer();
        
        if (success) {
            alertNotificationService.sendInfoAlert(
                "Server Restart Initiated",
                String.format("User '%s' initiated server restart", username)
            );
            return Map.of("success", true, "message", "Server restart initiated");
        } else {
            return Map.of("success", false, "message", "Failed to restart server");
        }
    }
    
    @GetMapping("/player/{playerName}")
    public String playerProfile(@PathVariable String playerName, Model model) {
        com.openmc.webapp.model.PlayerProfile profile = activityTrackerService.getPlayerProfile(playerName);
        
        if (profile == null) {
            model.addAttribute("error", "Player not found: " + playerName);
            model.addAttribute("playerName", playerName);
        } else {
            model.addAttribute("profile", profile);
        }
        
        model.addAttribute("dashboardTitle", serverConfig.getDashboardTitle());
        model.addAttribute("dashboardSubtitle", serverConfig.getDashboardSubtitle());
        model.addAttribute("dashboardPrimaryColor", serverConfig.getDashboardPrimaryColor());
        model.addAttribute("dashboardSecondaryColor", serverConfig.getDashboardSecondaryColor());
        
        return "player";
    }
    
    @GetMapping("/api/player/{playerName}")
    @ResponseBody
    public ResponseEntity<com.openmc.webapp.model.PlayerProfile> getPlayerProfile(@PathVariable String playerName) {
        com.openmc.webapp.model.PlayerProfile profile = activityTrackerService.getPlayerProfile(playerName);
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(profile);
    }
}
