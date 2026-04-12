package com.openmc.webapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for plugin delete endpoint
 */
public class PluginDeleteRequest {
    @Schema(description = "Admin username for authentication")
    private String username;
    @Schema(description = "Admin password for authentication")
    private String password;
    @Schema(description = "Name of the plugin JAR file to delete")
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
