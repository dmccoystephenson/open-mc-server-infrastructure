package com.openmc.agentmanager.service;

import com.openmc.agentmanager.model.AnthropicResponse;
import com.openmc.agentmanager.model.ToolDefinition;
import com.openmc.agentmanager.model.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentService Tests")
class AgentServiceTest {

    private static final List<ToolDefinition> ALL_TOOLS = ToolDefinition.allTools();
    private static final String ROLE_ADMIN = "Admin";

    @Mock
    private AnthropicService anthropicService;

    @Mock
    private ToolExecutionService toolExecutionService;

    @Mock
    private ConfirmationService confirmationService;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private AgentService agentService;

    @Test
    @DisplayName("Should return text response when no tool call is made")
    void shouldReturnTextResponseWhenNoToolCall() {
        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(AnthropicResponse.ContentBlock.builder()
                        .type("text")
                        .text("I can help you manage the server.")
                        .build()))
                .build();

        when(anthropicService.sendMessage(eq("hello"), anyList(), anyString())).thenReturn(response);
        when(anthropicService.findToolUseBlock(response)).thenReturn(null);
        when(anthropicService.extractTextContent(response)).thenReturn("I can help you manage the server.");

        AgentService.AgentResponse result = agentService.processMessage("hello", "testuser", ALL_TOOLS, ROLE_ADMIN);

        assertFalse(result.requiresConfirmation());
        assertEquals("I can help you manage the server.", result.textResponse());
    }

    @Test
    @DisplayName("Should require confirmation when tool needs it")
    void shouldRequireConfirmationWhenToolNeedsIt() {
        AnthropicResponse.ContentBlock toolBlock = AnthropicResponse.ContentBlock.builder()
                .type("tool_use")
                .id("tool-1")
                .name("start_server")
                .build();

        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(toolBlock))
                .build();

        when(anthropicService.sendMessage(eq("start the server"), anyList(), anyString())).thenReturn(response);
        when(anthropicService.findToolUseBlock(response)).thenReturn(toolBlock);
        when(toolExecutionService.isRecognizedTool("start_server")).thenReturn(true);
        when(confirmationService.requiresConfirmation("start_server")).thenReturn(true);

        AgentService.AgentResponse result = agentService.processMessage("start the server", "testuser", ALL_TOOLS, ROLE_ADMIN);

        assertTrue(result.requiresConfirmation());
        assertEquals("start_server", result.toolName());
        assertTrue(result.textResponse().contains("start"));
    }

    @Test
    @DisplayName("Should execute tool immediately when no confirmation needed")
    void shouldExecuteToolImmediatelyWhenNoConfirmationNeeded() {
        AnthropicResponse.ContentBlock toolBlock = AnthropicResponse.ContentBlock.builder()
                .type("tool_use")
                .id("tool-1")
                .name("start_server")
                .build();

        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(toolBlock))
                .build();

        ToolResult toolResult = ToolResult.builder()
                .toolUseId("tool-1")
                .toolName("start_server")
                .success(true)
                .message("Server start initiated")
                .build();

        AnthropicResponse followUpResponse = AnthropicResponse.builder()
                .content(List.of(AnthropicResponse.ContentBlock.builder()
                        .type("text")
                        .text("The server has been started.")
                        .build()))
                .build();

        when(anthropicService.sendMessage(eq("start the server"), anyList(), anyString())).thenReturn(response);
        when(anthropicService.findToolUseBlock(response)).thenReturn(toolBlock);
        when(toolExecutionService.isRecognizedTool("start_server")).thenReturn(true);
        when(confirmationService.requiresConfirmation("start_server")).thenReturn(false);
        when(toolExecutionService.executeTool("tool-1", "start_server", null)).thenReturn(toolResult);
        when(anthropicService.sendToolResult(eq("start the server"), anyList(), eq(toolResult), anyList(), anyString()))
                .thenReturn(followUpResponse);
        when(anthropicService.extractTextContent(followUpResponse)).thenReturn("The server has been started.");

        AgentService.AgentResponse result = agentService.processMessage("start the server", "testuser", ALL_TOOLS, ROLE_ADMIN);

        assertFalse(result.requiresConfirmation());
        assertEquals("The server has been started.", result.textResponse());
        verify(toolExecutionService).executeTool("tool-1", "start_server", null);
        verify(alertService).sendToolExecutionAlert("testuser", "start_server", "start the server", true, ROLE_ADMIN);
    }

    @Test
    @DisplayName("Should handle null response from Anthropic API")
    void shouldHandleNullResponseFromAnthropicApi() {
        when(anthropicService.sendMessage(eq("test"), anyList(), anyString())).thenReturn(null);

        AgentService.AgentResponse result = agentService.processMessage("test", "testuser", ALL_TOOLS, ROLE_ADMIN);

        assertFalse(result.requiresConfirmation());
        assertTrue(result.textResponse().contains("sorry"));
    }

    @Test
    @DisplayName("Should provide fallback response when follow-up API call fails")
    void shouldProvideFallbackResponseWhenFollowUpFails() {
        AnthropicResponse.ContentBlock toolBlock = AnthropicResponse.ContentBlock.builder()
                .type("tool_use")
                .id("tool-1")
                .name("stop_server")
                .build();

        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(toolBlock))
                .build();

        ToolResult toolResult = ToolResult.builder()
                .toolUseId("tool-1")
                .toolName("stop_server")
                .success(true)
                .message("Server stop initiated")
                .build();

        when(anthropicService.sendMessage(eq("stop the server"), anyList(), anyString())).thenReturn(response);
        when(anthropicService.findToolUseBlock(response)).thenReturn(toolBlock);
        when(toolExecutionService.isRecognizedTool("stop_server")).thenReturn(true);
        when(confirmationService.requiresConfirmation("stop_server")).thenReturn(false);
        when(toolExecutionService.executeTool("tool-1", "stop_server", null)).thenReturn(toolResult);
        when(anthropicService.sendToolResult(eq("stop the server"), anyList(), eq(toolResult), anyList(), anyString()))
                .thenThrow(new RuntimeException("API error"));

        AgentService.AgentResponse result = agentService.processMessage("stop the server", "testuser", ALL_TOOLS, ROLE_ADMIN);

        assertFalse(result.requiresConfirmation());
        assertTrue(result.textResponse().contains("✅"));
        verify(alertService).sendToolExecutionAlert("testuser", "stop_server", "stop the server", true, ROLE_ADMIN);
    }

    @Test
    @DisplayName("Should reject unrecognized tool from Anthropic")
    void shouldRejectUnrecognizedTool() {
        AnthropicResponse.ContentBlock toolBlock = AnthropicResponse.ContentBlock.builder()
                .type("tool_use")
                .id("tool-1")
                .name("delete_world")
                .build();

        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(toolBlock))
                .build();

        when(anthropicService.sendMessage(eq("delete the world"), anyList(), anyString())).thenReturn(response);
        when(anthropicService.findToolUseBlock(response)).thenReturn(toolBlock);
        when(toolExecutionService.isRecognizedTool("delete_world")).thenReturn(false);

        AgentService.AgentResponse result = agentService.processMessage("delete the world", "testuser", ALL_TOOLS, ROLE_ADMIN);

        assertFalse(result.requiresConfirmation());
        assertTrue(result.textResponse().contains("don't have the ability"));
        verify(toolExecutionService, never()).executeTool(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should send alert with failure status when tool execution fails")
    void shouldSendAlertWithFailureStatusWhenToolExecutionFails() {
        AnthropicResponse.ContentBlock toolBlock = AnthropicResponse.ContentBlock.builder()
                .type("tool_use")
                .id("tool-1")
                .name("restart_server")
                .build();

        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(toolBlock))
                .build();

        ToolResult toolResult = ToolResult.builder()
                .toolUseId("tool-1")
                .toolName("restart_server")
                .success(false)
                .message("Connection refused")
                .build();

        when(anthropicService.sendMessage(eq("restart the server"), anyList(), anyString())).thenReturn(response);
        when(anthropicService.findToolUseBlock(response)).thenReturn(toolBlock);
        when(toolExecutionService.isRecognizedTool("restart_server")).thenReturn(true);
        when(confirmationService.requiresConfirmation("restart_server")).thenReturn(false);
        when(toolExecutionService.executeTool("tool-1", "restart_server", null)).thenReturn(toolResult);
        when(anthropicService.sendToolResult(eq("restart the server"), anyList(), eq(toolResult), anyList(), anyString()))
                .thenThrow(new RuntimeException("API error"));

        AgentService.AgentResponse result = agentService.processMessage("restart the server", "testuser", ALL_TOOLS, ROLE_ADMIN);

        assertFalse(result.requiresConfirmation());
        assertTrue(result.textResponse().contains("❌"));
        verify(alertService).sendToolExecutionAlert("testuser", "restart_server", "restart the server", false, ROLE_ADMIN);
    }

    @Test
    @DisplayName("Should provide default message when text response is empty")
    void shouldProvideDefaultMessageWhenTextResponseIsEmpty() {
        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(AnthropicResponse.ContentBlock.builder()
                        .type("text")
                        .text("")
                        .build()))
                .build();

        when(anthropicService.sendMessage(eq("hi"), anyList(), anyString())).thenReturn(response);
        when(anthropicService.findToolUseBlock(response)).thenReturn(null);
        when(anthropicService.extractTextContent(response)).thenReturn("");

        AgentService.AgentResponse result = agentService.processMessage("hi", "testuser", ALL_TOOLS, ROLE_ADMIN);

        assertFalse(result.requiresConfirmation());
        assertTrue(result.textResponse().contains("start, stop, or restart"));
    }

    @Test
    @DisplayName("Should preserve user message in confirmation response")
    void shouldPreserveUserMessageInConfirmationResponse() {
        AnthropicResponse.ContentBlock toolBlock = AnthropicResponse.ContentBlock.builder()
                .type("tool_use")
                .id("tool-1")
                .name("stop_server")
                .build();

        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(toolBlock))
                .build();

        when(anthropicService.sendMessage(eq("please stop"), anyList(), anyString())).thenReturn(response);
        when(anthropicService.findToolUseBlock(response)).thenReturn(toolBlock);
        when(toolExecutionService.isRecognizedTool("stop_server")).thenReturn(true);
        when(confirmationService.requiresConfirmation("stop_server")).thenReturn(true);

        AgentService.AgentResponse result = agentService.processMessage("please stop", "testuser", ALL_TOOLS, ROLE_ADMIN);

        assertTrue(result.requiresConfirmation());
        assertEquals("please stop", result.userMessage());
        assertEquals("tool-1", result.toolUseId());
        assertNotNull(result.assistantContent());
    }

    @Test
    @DisplayName("Should not send alert when unrecognized tool is rejected")
    void shouldNotSendAlertWhenUnrecognizedToolRejected() {
        AnthropicResponse.ContentBlock toolBlock = AnthropicResponse.ContentBlock.builder()
                .type("tool_use")
                .id("tool-1")
                .name("unknown_tool")
                .build();

        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(toolBlock))
                .build();

        when(anthropicService.sendMessage(eq("do something"), anyList(), anyString())).thenReturn(response);
        when(anthropicService.findToolUseBlock(response)).thenReturn(toolBlock);
        when(toolExecutionService.isRecognizedTool("unknown_tool")).thenReturn(false);

        agentService.processMessage("do something", "testuser", ALL_TOOLS, ROLE_ADMIN);

        verify(alertService, never()).sendToolExecutionAlert(anyString(), anyString(), anyString(), anyBoolean(), anyString());
    }

    @Test
    @DisplayName("Should execute confirmed tool and respond via executeToolAndRespond")
    void shouldExecuteConfirmedToolAndRespond() {
        ToolResult toolResult = ToolResult.builder()
                .toolUseId("tool-1")
                .toolName("start_server")
                .success(true)
                .message("Server started")
                .build();

        AnthropicResponse followUpResponse = AnthropicResponse.builder()
                .content(List.of(AnthropicResponse.ContentBlock.builder()
                        .type("text")
                        .text("Server is now running!")
                        .build()))
                .build();

        when(toolExecutionService.executeTool("tool-1", "start_server", null)).thenReturn(toolResult);
        when(anthropicService.sendToolResult(eq("start the server"), anyList(), eq(toolResult), anyList(), anyString()))
                .thenReturn(followUpResponse);
        when(anthropicService.extractTextContent(followUpResponse)).thenReturn("Server is now running!");

        AgentService.AgentResponse result = agentService.executeToolAndRespond(
                "start the server", List.of(), "tool-1", "start_server", "player1", null, ALL_TOOLS, ROLE_ADMIN);

        assertFalse(result.requiresConfirmation());
        assertEquals("Server is now running!", result.textResponse());
        verify(alertService).sendToolExecutionAlert("player1", "start_server", "start the server", true, ROLE_ADMIN);
    }

    @Test
    @DisplayName("Should return polite refusal when user has no permitted tools")
    void shouldReturnPoliteRefusalWhenUserHasNoPermittedTools() {
        AgentService.AgentResponse result = agentService.processMessage(
                "start the server", "unauthorized-user", List.of(), "Unrecognized");

        assertFalse(result.requiresConfirmation());
        assertTrue(result.textResponse().contains("permission"));
        verify(anthropicService, never()).sendMessage(anyString(), anyList(), anyString());
        verify(alertService, never()).sendToolExecutionAlert(anyString(), anyString(), anyString(), anyBoolean(), anyString());
    }
}
