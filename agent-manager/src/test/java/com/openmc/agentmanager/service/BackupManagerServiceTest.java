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
@DisplayName("BackupManagerService Tests")
class BackupManagerServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private BackupManagerService backupManagerService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(backupManagerService, "backupManagerUrl", "http://test:8091");
    }

    @Test
    @DisplayName("Should call trigger backup endpoint successfully")
    void shouldCallTriggerBackupEndpoint() {
        ResponseEntity<String> mockResponse = new ResponseEntity<>("{\"success\":true,\"message\":\"Backup created\"}", HttpStatus.OK);
        when(restTemplate.exchange(eq("http://test:8091/api/backups/trigger"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        String result = backupManagerService.triggerBackup();

        assertEquals("{\"success\":true,\"message\":\"Backup created\"}", result);
        verify(restTemplate).exchange(eq("http://test:8091/api/backups/trigger"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Should return default message when response body is null")
    void shouldReturnDefaultMessageWhenBodyIsNull() {
        ResponseEntity<String> mockResponse = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.exchange(eq("http://test:8091/api/backups/trigger"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        String result = backupManagerService.triggerBackup();

        assertEquals("Backup triggered", result);
    }

    @Test
    @DisplayName("Should throw exception on connection failure")
    void shouldThrowExceptionOnConnectionFailure() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThrows(RuntimeException.class, () -> backupManagerService.triggerBackup());
    }
}
