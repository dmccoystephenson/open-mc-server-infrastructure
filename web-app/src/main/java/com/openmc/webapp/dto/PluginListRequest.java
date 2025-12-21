package com.openmc.webapp.dto;

/**
 * Request DTO for plugin list endpoint
 */
public class PluginListRequest {
    private String username;
    private String password;
    
    public PluginListRequest() {
    }
    
    public PluginListRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}
