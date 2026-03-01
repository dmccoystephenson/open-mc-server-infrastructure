package com.openmc.agentmanager.service;

import com.openmc.agentmanager.model.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolExecutionService Tests")
class ToolExecutionServiceTest {

    @Mock
    private MinecraftWrapperService minecraftWrapperService;

    @Mock
    private BackupManagerService backupManagerService;

    @InjectMocks
    private ToolExecutionService toolExecutionService;

    @Test
    @DisplayName("Should execute start_server tool successfully")
    void shouldExecuteStartServerTool() {
        when(minecraftWrapperService.startServer()).thenReturn("Server start initiated");

        ToolResult result = toolExecutionService.executeTool("tool-1", "start_server");

        assertTrue(result.isSuccess());
        assertEquals("Server start initiated", result.getMessage());
        assertEquals("start_server", result.getToolName());
        assertEquals("tool-1", result.getToolUseId());
        verify(minecraftWrapperService).startServer();
    }

    @Test
    @DisplayName("Should execute stop_server tool successfully")
    void shouldExecuteStopServerTool() {
        when(minecraftWrapperService.stopServer()).thenReturn("Server stop initiated");

        ToolResult result = toolExecutionService.executeTool("tool-2", "stop_server");

        assertTrue(result.isSuccess());
        assertEquals("Server stop initiated", result.getMessage());
        assertEquals("stop_server", result.getToolName());
        verify(minecraftWrapperService).stopServer();
    }

    @Test
    @DisplayName("Should execute restart_server tool successfully")
    void shouldExecuteRestartServerTool() {
        when(minecraftWrapperService.restartServer()).thenReturn("Server restart initiated");

        ToolResult result = toolExecutionService.executeTool("tool-3", "restart_server");

        assertTrue(result.isSuccess());
        assertEquals("Server restart initiated", result.getMessage());
        assertEquals("restart_server", result.getToolName());
        verify(minecraftWrapperService).restartServer();
    }

    @Test
    @DisplayName("Should execute get_server_status tool successfully")
    void shouldExecuteGetServerStatusTool() {
        when(minecraftWrapperService.getServerStatus()).thenReturn("{\"running\":true}");

        ToolResult result = toolExecutionService.executeTool("tool-4", "get_server_status");

        assertTrue(result.isSuccess());
        assertEquals("{\"running\":true}", result.getMessage());
        assertEquals("get_server_status", result.getToolName());
        assertEquals("tool-4", result.getToolUseId());
        verify(minecraftWrapperService).getServerStatus();
    }

    @Test
    @DisplayName("Should execute trigger_backup tool successfully")
    void shouldExecuteTriggerBackupTool() {
        when(backupManagerService.triggerBackup()).thenReturn("{\"success\":true,\"message\":\"Backup created\"}");

        ToolResult result = toolExecutionService.executeTool("tool-5", "trigger_backup");

        assertTrue(result.isSuccess());
        assertEquals("{\"success\":true,\"message\":\"Backup created\"}", result.getMessage());
        assertEquals("trigger_backup", result.getToolName());
        assertEquals("tool-5", result.getToolUseId());
        verify(backupManagerService).triggerBackup();
    }

    @Test
    @DisplayName("Should return failure for unknown tool")
    void shouldReturnFailureForUnknownTool() {
        ToolResult result = toolExecutionService.executeTool("tool-6", "unknown_tool");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Unknown tool"));
        assertEquals("unknown_tool", result.getToolName());
    }

    @Test
    @DisplayName("Should handle wrapper service exception gracefully")
    void shouldHandleWrapperServiceException() {
        when(minecraftWrapperService.startServer())
                .thenThrow(new RuntimeException("Connection refused"));

        ToolResult result = toolExecutionService.executeTool("tool-7", "start_server");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Connection refused"));
    }

    @Test
    @DisplayName("Should recognize valid tool names")
    void shouldRecognizeValidToolNames() {
        assertTrue(toolExecutionService.isRecognizedTool("start_server"));
        assertTrue(toolExecutionService.isRecognizedTool("stop_server"));
        assertTrue(toolExecutionService.isRecognizedTool("restart_server"));
        assertTrue(toolExecutionService.isRecognizedTool("get_server_status"));
        assertTrue(toolExecutionService.isRecognizedTool("trigger_backup"));
    }

    @Test
    @DisplayName("Should not recognize invalid tool names")
    void shouldNotRecognizeInvalidToolNames() {
        assertFalse(toolExecutionService.isRecognizedTool("unknown_tool"));
        assertFalse(toolExecutionService.isRecognizedTool(""));
        assertFalse(toolExecutionService.isRecognizedTool(null));
    }
}
