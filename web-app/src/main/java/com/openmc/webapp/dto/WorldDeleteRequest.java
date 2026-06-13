package com.openmc.webapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class WorldDeleteRequest {
    @Schema(description = "Admin username for authentication")
    private String username;
    @Schema(description = "Admin password for authentication")
    private String password;
    @Schema(description = "Name of the world directory to delete")
    private String name;

    public WorldDeleteRequest() {}

    public WorldDeleteRequest(String username, String password, String name) {
        this.username = username;
        this.password = password;
        this.name = name;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
