package com.openmc.agentmanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MinecraftWrapperService Tests")
class MinecraftWrapperServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MinecraftWrapperService minecraftWrapperService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(minecraftWrapperService, "wrapperUrl", "http://test:8092");
    }

    @Test
    @DisplayName("Should call start endpoint successfully")
    void shouldCallStartEndpoint() {
        ResponseEntity<String> mockResponse = new ResponseEntity<>("Server start initiated", HttpStatus.ACCEPTED);
        when(restTemplate.exchange(eq("http://test:8092/api/server/start"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        String result = minecraftWrapperService.startServer();

        assertEquals("Server start initiated", result);
        verify(restTemplate).exchange(eq("http://test:8092/api/server/start"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Should call stop endpoint successfully")
    void shouldCallStopEndpoint() {
        ResponseEntity<String> mockResponse = new ResponseEntity<>("Server stop initiated", HttpStatus.ACCEPTED);
        when(restTemplate.exchange(eq("http://test:8092/api/server/stop"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        String result = minecraftWrapperService.stopServer();

        assertEquals("Server stop initiated", result);
        verify(restTemplate).exchange(eq("http://test:8092/api/server/stop"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Should call restart endpoint successfully")
    void shouldCallRestartEndpoint() {
        ResponseEntity<String> mockResponse = new ResponseEntity<>("Server restart initiated", HttpStatus.ACCEPTED);
        when(restTemplate.exchange(eq("http://test:8092/api/server/restart"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        String result = minecraftWrapperService.restartServer();

        assertEquals("Server restart initiated", result);
        verify(restTemplate).exchange(eq("http://test:8092/api/server/restart"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Should call status endpoint successfully")
    void shouldCallStatusEndpoint() {
        ResponseEntity<String> mockResponse = new ResponseEntity<>("{\"running\":true}", HttpStatus.OK);
        when(restTemplate.getForEntity(eq("http://test:8092/api/server/status"), eq(String.class)))
                .thenReturn(mockResponse);

        String result = minecraftWrapperService.getServerStatus();

        assertEquals("{\"running\":true}", result);
        verify(restTemplate).getForEntity(eq("http://test:8092/api/server/status"), eq(String.class));
    }

    @Test
    @DisplayName("Should throw exception on connection failure")
    void shouldThrowExceptionOnConnectionFailure() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThrows(RuntimeException.class, () -> minecraftWrapperService.startServer());
    }

    @Test
    @DisplayName("Should call server logs endpoint successfully")
    void shouldCallServerLogsEndpoint() {
        String logJson = "{\"lines\":[\"line1\",\"line2\"],\"count\":2}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(logJson, HttpStatus.OK);
        when(restTemplate.getForEntity(eq("http://test:8092/api/server/logs?lines=50"), eq(String.class)))
                .thenReturn(mockResponse);

        String result = minecraftWrapperService.getServerLogs(50);

        assertEquals(logJson, result);
        verify(restTemplate).getForEntity(eq("http://test:8092/api/server/logs?lines=50"), eq(String.class));
    }

    @Test
    @DisplayName("Should return empty log JSON when server logs endpoint returns null body")
    void shouldReturnEmptyLogJsonWhenNullBody() {
        ResponseEntity<String> mockResponse = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.getForEntity(eq("http://test:8092/api/server/logs?lines=10"), eq(String.class)))
                .thenReturn(mockResponse);

        String result = minecraftWrapperService.getServerLogs(10);

        assertEquals("{\"lines\":[],\"count\":0}", result);
    }

    @Test
    @DisplayName("Should throw exception when server logs endpoint fails")
    void shouldThrowExceptionWhenServerLogsEndpointFails() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("403 Forbidden"));

        assertThrows(RuntimeException.class, () -> minecraftWrapperService.getServerLogs(50));
    }
}
