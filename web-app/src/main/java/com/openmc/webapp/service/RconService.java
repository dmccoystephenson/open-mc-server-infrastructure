package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.rcon.RconClient;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class RconService {
    
    private final ServerConfig serverConfig;
    
    public RconService(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }
    
    public String sendCommand(String command) {
        try (RconClient rcon = new RconClient(serverConfig.getHost(), serverConfig.getRconPort(), 
                                              serverConfig.getRconPassword())) {
            return rcon.sendCommand(command);
        } catch (IOException e) {
            return "Error: Unable to connect to server - " + e.getMessage();
        }
    }
    
    public ServerStatus getServerStatus() {
        String response = sendCommand("list");
        ResourceUsage resourceUsage = getResourceUsage();
        return new ServerStatus(serverConfig, response, resourceUsage);
    }
    
    public ResourceUsage getResourceUsage() {
        String tpsResponse = sendCommand("tps");
        
        // Default values when unable to fetch
        String tps = "N/A";
        String memoryUsed = "N/A";
        String memoryMax = "N/A";
        String memoryFree = "N/A";
        double memoryUsedPercent = 0.0;
        
        if (!tpsResponse.startsWith("Error:")) {
            // Parse TPS response
            // Example format: "TPS from last 1m, 5m, 15m: 20.0, 20.0, 20.0"
            if (tpsResponse.contains("TPS")) {
                tps = parseTps(tpsResponse);
            }
            
            // Parse memory information if present
            // Example format might include: "Memory: 1024MB/2048MB"
            if (tpsResponse.contains("Memory") || tpsResponse.contains("memory")) {
                String[] memoryParts = parseMemory(tpsResponse);
                if (memoryParts.length >= 3) {
                    memoryUsed = memoryParts[0];
                    memoryMax = memoryParts[1];
                    memoryFree = memoryParts[2];
                }
            }
        }
        
        // Try to get memory from forge tps command if standard tps didn't provide it
        if ("N/A".equals(memoryUsed)) {
            String forgeResponse = sendCommand("forge tps");
            if (!forgeResponse.startsWith("Error:") && forgeResponse.contains("Memory")) {
                String[] memoryParts = parseMemory(forgeResponse);
                if (memoryParts.length >= 3) {
                    memoryUsed = memoryParts[0];
                    memoryMax = memoryParts[1];
                    memoryFree = memoryParts[2];
                }
            }
        }
        
        // Calculate memory usage percentage
        if (!"N/A".equals(memoryUsed) && !"N/A".equals(memoryMax)) {
            try {
                double used = parseMemoryValue(memoryUsed);
                double max = parseMemoryValue(memoryMax);
                if (max > 0) {
                    memoryUsedPercent = (used / max) * 100.0;
                }
            } catch (Exception e) {
                // Keep default 0.0 if parsing fails
            }
        }
        
        return new ResourceUsage(tps, memoryUsed, memoryMax, memoryFree, memoryUsedPercent);
    }
    
    private String parseTps(String response) {
        // Extract TPS values from response
        // Common formats:
        // "TPS from last 1m, 5m, 15m: 20.0, 20.0, 20.0"
        // "§6TPS from last 1m, 5m, 15m: §a20.0§6, §a20.0§6, §a20.0"
        
        // Remove color codes
        String cleaned = response.replaceAll("§[0-9a-fk-or]", "");
        
        if (cleaned.contains(":")) {
            String[] parts = cleaned.split(":");
            if (parts.length > 1) {
                return parts[1].trim();
            }
        }
        
        return response.trim();
    }
    
    private String[] parseMemory(String response) {
        // Try to extract memory information
        // Common formats:
        // "Memory: 1024MB/2048MB"
        // "Mem: 50.0% 1024MB/2048MB"
        
        String[] result = new String[3]; // used, max, free
        result[0] = "N/A";
        result[1] = "N/A";
        result[2] = "N/A";
        
        // Remove color codes
        String cleaned = response.replaceAll("§[0-9a-fk-or]", "");
        
        // Look for pattern like "1024MB/2048MB" or "1024M/2048M"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)[MG]B?\\s*/\\s*(\\d+(?:\\.\\d+)?)[MG]B?");
        java.util.regex.Matcher matcher = pattern.matcher(cleaned);
        
        if (matcher.find()) {
            String usedStr = matcher.group(1);
            String maxStr = matcher.group(2);
            
            // Determine the unit from the match
            String unit = "MB";
            if (cleaned.substring(matcher.start(), matcher.end()).contains("G")) {
                unit = "GB";
            }
            
            result[0] = usedStr + unit;
            result[1] = maxStr + unit;
            
            // Calculate free memory
            try {
                double used = Double.parseDouble(usedStr);
                double max = Double.parseDouble(maxStr);
                double free = max - used;
                result[2] = String.format("%.1f%s", free, unit);
            } catch (Exception e) {
                result[2] = "N/A";
            }
        }
        
        return result;
    }
    
    private double parseMemoryValue(String memoryStr) {
        // Parse memory string like "1024MB" or "2.5GB" to MB
        if (memoryStr == null || memoryStr.equals("N/A")) {
            return 0.0;
        }
        
        String cleaned = memoryStr.replaceAll("[^0-9.]", "");
        double value = Double.parseDouble(cleaned);
        
        if (memoryStr.contains("GB") || memoryStr.contains("G")) {
            value *= 1024; // Convert GB to MB
        }
        
        return value;
    }
    
    public static class ServerStatus {
        private final String motd;
        private final int maxPlayers;
        private final String playerList;
        private final boolean online;
        private final ResourceUsage resourceUsage;
        
        public ServerStatus(ServerConfig config, String playerListResponse, ResourceUsage resourceUsage) {
            this.motd = config.getMotd();
            this.maxPlayers = config.getMaxPlayers();
            this.playerList = playerListResponse;
            this.online = !playerListResponse.startsWith("Error:");
            this.resourceUsage = resourceUsage;
        }
        
        public String getMotd() {
            return motd;
        }
        
        public int getMaxPlayers() {
            return maxPlayers;
        }
        
        public String getPlayerList() {
            return playerList;
        }
        
        public boolean isOnline() {
            return online;
        }
        
        public ResourceUsage getResourceUsage() {
            return resourceUsage;
        }
    }
    
    public static class ResourceUsage {
        private final String tps;
        private final String memoryUsed;
        private final String memoryMax;
        private final String memoryFree;
        private final double memoryUsedPercent;
        
        public ResourceUsage(String tps, String memoryUsed, String memoryMax, String memoryFree, double memoryUsedPercent) {
            this.tps = tps;
            this.memoryUsed = memoryUsed;
            this.memoryMax = memoryMax;
            this.memoryFree = memoryFree;
            this.memoryUsedPercent = memoryUsedPercent;
        }
        
        public String getTps() {
            return tps;
        }
        
        public String getMemoryUsed() {
            return memoryUsed;
        }
        
        public String getMemoryMax() {
            return memoryMax;
        }
        
        public String getMemoryFree() {
            return memoryFree;
        }
        
        public double getMemoryUsedPercent() {
            return memoryUsedPercent;
        }
    }
}
