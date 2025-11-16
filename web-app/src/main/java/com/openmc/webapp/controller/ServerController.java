package com.openmc.webapp.controller;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.model.ActivityTrackerStats;
import com.openmc.webapp.model.LeaderboardEntry;
import com.openmc.webapp.service.ActivityTrackerService;
import com.openmc.webapp.service.RconService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
public class ServerController {
    
    private static final Logger logger = LoggerFactory.getLogger(ServerController.class);
    
    private final RconService rconService;
    private final ServerConfig serverConfig;
    private final ActivityTrackerService activityTrackerService;
    
    public ServerController(RconService rconService, ServerConfig serverConfig, 
                          ActivityTrackerService activityTrackerService) {
        this.rconService = rconService;
        this.serverConfig = serverConfig;
        this.activityTrackerService = activityTrackerService;
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
            return Map.of("result", "Error: Username and password are required");
        }
        
        if (!serverConfig.getAdminUsername().equals(username) || 
            !serverConfig.getAdminPassword().equals(password)) {
            return Map.of("result", "Error: Invalid username or password");
        }
        
        // Validate command
        if (command == null || command.trim().isEmpty()) {
            return Map.of("result", "Error: Command cannot be empty");
        }
        
        String result = rconService.sendCommand(command);
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
    
    // Allow/Deny List Management Endpoints
    
    private boolean validateAdminCredentials(String username, String password) {
        return username != null && password != null &&
               serverConfig.getAdminUsername().equals(username) &&
               serverConfig.getAdminPassword().equals(password);
    }
    
    private boolean isValidMinecraftUsername(String username) {
        // Minecraft usernames are 3-16 characters, alphanumeric and underscores only
        if (username == null || username.length() < 3 || username.length() > 16) {
            return false;
        }
        return username.matches("^[a-zA-Z0-9_]+$");
    }
    
    @PostMapping("/api/allowlist/toggle")
    @ResponseBody
    public Map<String, String> toggleAllowList(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        String action = payload.get("action"); // "on" or "off"
        
        if (!validateAdminCredentials(username, password)) {
            return Map.of("result", "Error: Invalid credentials");
        }
        
        if (action == null || (!action.equals("on") && !action.equals("off"))) {
            return Map.of("result", "Error: Action must be 'on' or 'off'");
        }
        
        String result = rconService.sendCommand("whitelist " + action);
        return Map.of("result", result);
    }
    
    @PostMapping("/api/allowlist/add")
    @ResponseBody
    public Map<String, String> addToAllowList(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        String player = payload.get("player");
        
        if (!validateAdminCredentials(username, password)) {
            return Map.of("result", "Error: Invalid credentials");
        }
        
        if (player == null || player.trim().isEmpty()) {
            return Map.of("result", "Error: Player name is required");
        }
        
        player = player.trim();
        if (!isValidMinecraftUsername(player)) {
            return Map.of("result", "Error: Invalid player name format (3-16 characters, alphanumeric and underscores only)");
        }
        
        String result = rconService.sendCommand("whitelist add " + player);
        return Map.of("result", result);
    }
    
    @PostMapping("/api/allowlist/remove")
    @ResponseBody
    public Map<String, String> removeFromAllowList(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        String player = payload.get("player");
        
        if (!validateAdminCredentials(username, password)) {
            return Map.of("result", "Error: Invalid credentials");
        }
        
        if (player == null || player.trim().isEmpty()) {
            return Map.of("result", "Error: Player name is required");
        }
        
        player = player.trim();
        if (!isValidMinecraftUsername(player)) {
            return Map.of("result", "Error: Invalid player name format (3-16 characters, alphanumeric and underscores only)");
        }
        
        String result = rconService.sendCommand("whitelist remove " + player);
        return Map.of("result", result);
    }
    
    @PostMapping("/api/allowlist/list")
    @ResponseBody
    public Map<String, String> listAllowList(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        
        if (!validateAdminCredentials(username, password)) {
            return Map.of("result", "Error: Invalid credentials");
        }
        
        String result = rconService.sendCommand("whitelist list");
        return Map.of("result", result);
    }
    
    @PostMapping("/api/ban/add")
    @ResponseBody
    public Map<String, String> banPlayer(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        String player = payload.get("player");
        String reason = payload.get("reason");
        
        if (!validateAdminCredentials(username, password)) {
            return Map.of("result", "Error: Invalid credentials");
        }
        
        if (player == null || player.trim().isEmpty()) {
            return Map.of("result", "Error: Player name is required");
        }
        
        player = player.trim();
        if (!isValidMinecraftUsername(player)) {
            return Map.of("result", "Error: Invalid player name format (3-16 characters, alphanumeric and underscores only)");
        }
        
        String command = "ban " + player;
        if (reason != null && !reason.trim().isEmpty()) {
            // Sanitize reason by removing special characters that could cause issues
            String sanitizedReason = reason.trim().replaceAll("[\\n\\r;]", " ");
            command += " " + sanitizedReason;
        }
        
        String result = rconService.sendCommand(command);
        return Map.of("result", result);
    }
    
    @PostMapping("/api/ban/remove")
    @ResponseBody
    public Map<String, String> unbanPlayer(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        String player = payload.get("player");
        
        if (!validateAdminCredentials(username, password)) {
            return Map.of("result", "Error: Invalid credentials");
        }
        
        if (player == null || player.trim().isEmpty()) {
            return Map.of("result", "Error: Player name is required");
        }
        
        player = player.trim();
        if (!isValidMinecraftUsername(player)) {
            return Map.of("result", "Error: Invalid player name format (3-16 characters, alphanumeric and underscores only)");
        }
        
        String result = rconService.sendCommand("pardon " + player);
        return Map.of("result", result);
    }
    
    @PostMapping("/api/ban/list")
    @ResponseBody
    public Map<String, String> listBans(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        
        if (!validateAdminCredentials(username, password)) {
            return Map.of("result", "Error: Invalid credentials");
        }
        
        String result = rconService.sendCommand("banlist");
        return Map.of("result", result);
    }
}
