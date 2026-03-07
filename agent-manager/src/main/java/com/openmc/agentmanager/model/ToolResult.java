package com.openmc.agentmanager.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the result of executing a tool.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    private String toolUseId;
    private String toolName;
    private boolean success;
    private String message;
}
