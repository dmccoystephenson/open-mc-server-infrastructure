package com.openmc.webapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class WorldListRequest {
    @Schema(description = "Admin username for authentication")
    private String username;
    @Schema(description = "Admin password for authentication")
    private String password;

    public WorldListRequest() {}

    public WorldListRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
