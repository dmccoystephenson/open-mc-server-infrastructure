package com.openmc.webapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for plugin operations (upload and delete)
 */
public class PluginOperationResponse {
    @Schema(description = "Whether the operation was successful")
    private boolean success;
    @Schema(description = "Success message")
    private String message;
    @Schema(description = "Error message if operation failed")
    private String error;
    
    public PluginOperationResponse() {
    }
    
    public PluginOperationResponse(boolean success, String message, String error) {
        this.success = success;
        this.message = message;
        this.error = error;
    }
    
    public static PluginOperationResponse success(String message) {
        return new PluginOperationResponse(true, message, null);
    }
    
    public static PluginOperationResponse error(String error) {
        return new PluginOperationResponse(false, null, error);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
}
