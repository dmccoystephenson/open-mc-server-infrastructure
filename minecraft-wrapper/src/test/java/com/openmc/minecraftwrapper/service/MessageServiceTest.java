package com.openmc.minecraftwrapper.service;

import com.openmc.minecraftwrapper.model.Alert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService Tests")
class MessageServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(messageService, "alertManagerUrl", "http://test:8090/api/alerts");
    }

    @Test
    @DisplayName("Should send message with correct parameters")
    void shouldSendMessageWithCorrectParameters() {
        messageService.sendMessage("Test message", "MINECRAFT");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("http://test:8090/api/alerts"), captor.capture(), eq(String.class));

        HttpEntity<Alert> entity = captor.getValue();
        Alert alert = entity.getBody();
        
        assertNotNull(alert);
        assertEquals("Test message", alert.getMessage());
        assertEquals("minecraft-server", alert.getSource());
        assertEquals("INFO", alert.getLevel());
        assertEquals(Collections.singletonList("MINECRAFT"), alert.getDestinations());
    }

    @Test
    @DisplayName("Should default to MINECRAFT destination")
    void shouldDefaultToMinecraftDestination() {
        messageService.sendMessage("Test message");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        Alert alert = (Alert) captor.getValue().getBody();
        assertNotNull(alert);
        assertEquals(Collections.singletonList("MINECRAFT"), alert.getDestinations());
    }

    @Test
    @DisplayName("Should convert destination to uppercase")
    void shouldConvertDestinationToUppercase() {
        messageService.sendMessage("Test", "minecraft");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        Alert alert = (Alert) captor.getValue().getBody();
        assertNotNull(alert);
        assertEquals(Collections.singletonList("MINECRAFT"), alert.getDestinations());
    }

    @Test
    @DisplayName("Should handle RestTemplate exception gracefully")
    void shouldHandleRestTemplateExceptionGracefully() {
        when(restTemplate.postForEntity(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Connection error"));

        assertDoesNotThrow(() -> messageService.sendMessage("Test", "MINECRAFT"));
    }
}
