package com.openmc.agentmanager.service;

import com.openmc.agentmanager.model.AnthropicResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnthropicService Tests")
class AnthropicServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AnthropicService anthropicService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(anthropicService, "apiKey", "test-key");
        ReflectionTestUtils.setField(anthropicService, "apiUrl", "https://api.anthropic.com/v1/messages");
        ReflectionTestUtils.setField(anthropicService, "model", "claude-sonnet-4-20250514");
    }

    @Test
    @DisplayName("Should extract text content from response")
    void shouldExtractTextContentFromResponse() {
        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(AnthropicResponse.ContentBlock.builder()
                        .type("text")
                        .text("Hello, I can help you manage the server.")
                        .build()))
                .build();

        String text = anthropicService.extractTextContent(response);

        assertEquals("Hello, I can help you manage the server.", text);
    }

    @Test
    @DisplayName("Should return empty string for null response")
    void shouldReturnEmptyStringForNullResponse() {
        assertEquals("", anthropicService.extractTextContent(null));
    }

    @Test
    @DisplayName("Should return empty string for response with no text blocks")
    void shouldReturnEmptyStringForNoTextBlocks() {
        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(AnthropicResponse.ContentBlock.builder()
                        .type("tool_use")
                        .id("tool-1")
                        .name("start_server")
                        .build()))
                .build();

        assertEquals("", anthropicService.extractTextContent(response));
    }

    @Test
    @DisplayName("Should find tool_use block in response")
    void shouldFindToolUseBlock() {
        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(
                        AnthropicResponse.ContentBlock.builder()
                                .type("text")
                                .text("I'll start the server.")
                                .build(),
                        AnthropicResponse.ContentBlock.builder()
                                .type("tool_use")
                                .id("tool-1")
                                .name("start_server")
                                .build()
                ))
                .build();

        AnthropicResponse.ContentBlock toolBlock = anthropicService.findToolUseBlock(response);

        assertNotNull(toolBlock);
        assertEquals("tool_use", toolBlock.getType());
        assertEquals("start_server", toolBlock.getName());
    }

    @Test
    @DisplayName("Should return null when no tool_use block found")
    void shouldReturnNullWhenNoToolUseBlock() {
        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(AnthropicResponse.ContentBlock.builder()
                        .type("text")
                        .text("I can help you.")
                        .build()))
                .build();

        assertNull(anthropicService.findToolUseBlock(response));
    }

    @Test
    @DisplayName("Should return null for null response when finding tool_use block")
    void shouldReturnNullForNullResponseFindingToolUse() {
        assertNull(anthropicService.findToolUseBlock(null));
    }

    @Test
    @DisplayName("Should concatenate multiple text blocks with newlines")
    void shouldConcatenateMultipleTextBlocks() {
        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(
                        AnthropicResponse.ContentBlock.builder()
                                .type("text")
                                .text("First block.")
                                .build(),
                        AnthropicResponse.ContentBlock.builder()
                                .type("tool_use")
                                .id("tool-1")
                                .name("start_server")
                                .build(),
                        AnthropicResponse.ContentBlock.builder()
                                .type("text")
                                .text("Second block.")
                                .build()
                ))
                .build();

        String text = anthropicService.extractTextContent(response);

        assertEquals("First block.\nSecond block.", text);
    }

    @Test
    @DisplayName("Should return empty string for response with null content list")
    void shouldReturnEmptyStringForNullContentList() {
        AnthropicResponse response = AnthropicResponse.builder()
                .content(null)
                .build();

        assertEquals("", anthropicService.extractTextContent(response));
    }

    @Test
    @DisplayName("Should return empty string for response with empty content list")
    void shouldReturnEmptyStringForEmptyContentList() {
        AnthropicResponse response = AnthropicResponse.builder()
                .content(Collections.emptyList())
                .build();

        assertEquals("", anthropicService.extractTextContent(response));
    }

    @Test
    @DisplayName("Should return null for findToolUseBlock with null content list")
    void shouldReturnNullForFindToolUseBlockWithNullContent() {
        AnthropicResponse response = AnthropicResponse.builder()
                .content(null)
                .build();

        assertNull(anthropicService.findToolUseBlock(response));
    }

    @Test
    @DisplayName("Should return null for findToolUseBlock with empty content list")
    void shouldReturnNullForFindToolUseBlockWithEmptyContent() {
        AnthropicResponse response = AnthropicResponse.builder()
                .content(Collections.emptyList())
                .build();

        assertNull(anthropicService.findToolUseBlock(response));
    }

    @Test
    @DisplayName("Should find first tool_use block when multiple exist")
    void shouldFindFirstToolUseBlockWhenMultipleExist() {
        AnthropicResponse response = AnthropicResponse.builder()
                .content(List.of(
                        AnthropicResponse.ContentBlock.builder()
                                .type("tool_use")
                                .id("tool-1")
                                .name("start_server")
                                .build(),
                        AnthropicResponse.ContentBlock.builder()
                                .type("tool_use")
                                .id("tool-2")
                                .name("stop_server")
                                .build()
                ))
                .build();

        AnthropicResponse.ContentBlock toolBlock = anthropicService.findToolUseBlock(response);

        assertNotNull(toolBlock);
        assertEquals("start_server", toolBlock.getName());
        assertEquals("tool-1", toolBlock.getId());
    }
}
