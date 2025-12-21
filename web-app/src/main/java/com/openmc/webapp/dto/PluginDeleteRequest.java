package com.openmc.webapp.dto;

/**
 * Request DTO for plugin delete endpoint
 */
public class PluginDeleteRequest {
    private String username;
    private String password;
    private String filename;
    
    public PluginDeleteRequest() {
    }
    
    public PluginDeleteRequest(String username, String password, String filename) {
        this.username = username;
        this.password = password;
        this.filename = filename;
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
    
    public String getFilename() {
        return filename;
    }
    
    public void setFilename(String filename) {
        this.filename = filename;
    }
}
