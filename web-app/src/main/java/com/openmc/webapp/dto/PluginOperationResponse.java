package com.openmc.webapp.dto;

/**
 * Response DTO for plugin operations (upload and delete)
 */
public class PluginOperationResponse {
    private boolean success;
    private String message;
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
