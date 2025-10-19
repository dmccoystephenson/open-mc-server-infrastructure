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
        return new ServerStatus(serverConfig, response);
    }
    
    public static class ServerStatus {
        private final String motd;
        private final int maxPlayers;
        private final String playerList;
        private final boolean online;
        
        public ServerStatus(ServerConfig config, String playerListResponse) {
            this.motd = config.getMotd();
            this.maxPlayers = config.getMaxPlayers();
            this.playerList = playerListResponse;
            this.online = !playerListResponse.startsWith("Error:");
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
    }
}
