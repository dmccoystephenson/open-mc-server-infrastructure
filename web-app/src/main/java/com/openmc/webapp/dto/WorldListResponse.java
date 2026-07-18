package com.openmc.webapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public class WorldListResponse {
    @Schema(description = "Whether the operation was successful")
    private boolean success;
    @Schema(description = "List of detected world directories")
    private List<WorldInfo> worlds;
    @Schema(description = "Error message if operation failed")
    private String error;

    public WorldListResponse() {}

    public WorldListResponse(boolean success, List<WorldInfo> worlds, String error) {
        this.success = success;
        this.worlds = worlds;
        this.error = error;
    }

    public static WorldListResponse success(List<WorldInfo> worlds) {
        return new WorldListResponse(true, worlds, null);
    }

    public static WorldListResponse error(String error) {
        return new WorldListResponse(false, null, error);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public List<WorldInfo> getWorlds() { return worlds; }
    public void setWorlds(List<WorldInfo> worlds) { this.worlds = worlds; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
