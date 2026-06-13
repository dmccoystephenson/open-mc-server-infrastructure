package com.openmc.webapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class WorldOperationResponse {
    @Schema(description = "Whether the operation was successful")
    private boolean success;
    @Schema(description = "Success message")
    private String message;
    @Schema(description = "Error message if operation failed")
    private String error;

    public WorldOperationResponse() {}

    public WorldOperationResponse(boolean success, String message, String error) {
        this.success = success;
        this.message = message;
        this.error = error;
    }

    public static WorldOperationResponse success(String message) {
        return new WorldOperationResponse(true, message, null);
    }

    public static WorldOperationResponse error(String error) {
        return new WorldOperationResponse(false, null, error);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
