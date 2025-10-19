package com.privatemc.webapp.controller;

import com.privatemc.webapp.config.ServerConfig;
import com.privatemc.webapp.service.RconService;
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
    public String index(Model model) {
        RconService.ServerStatus status = rconService.getServerStatus();
        model.addAttribute("status", status);
        model.addAttribute("dynmapUrl", serverConfig.getDynmapUrl());
        model.addAttribute("bluemapUrl", serverConfig.getBluemapUrl());
        return "index";
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
}
