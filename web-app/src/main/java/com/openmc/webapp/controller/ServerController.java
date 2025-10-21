package com.openmc.webapp.controller;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.service.RconService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class ServerController {
    
    private final RconService rconService;
    private final ServerConfig serverConfig;
    
    public ServerController(RconService rconService, ServerConfig serverConfig) {
        this.rconService = rconService;
        this.serverConfig = serverConfig;
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
        return "public";
    }
    
    @GetMapping("/admin")
    public String adminPage() {
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
}
