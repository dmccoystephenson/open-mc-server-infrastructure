package com.openmc.minecraftwrapper.service;

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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebAppNotificationService Tests")
class WebAppNotificationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private WebAppNotificationService webAppNotificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(webAppNotificationService, "webappUrl", "http://webapp:8080");
        ReflectionTestUtils.setField(webAppNotificationService, "deploymentAuthToken", "test-token");
    }

    @Test
    @DisplayName("Should skip notification when webapp url is not configured")
    void shouldSkipNotificationWhenUrlNotConfigured() {
        ReflectionTestUtils.setField(webAppNotificationService, "webappUrl", "");

        webAppNotificationService.notifyDeploymentSuccess("myplugin", "main", "https://example.com/repo.git");

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("Should skip notification when webapp url is blank")
    void shouldSkipNotificationWhenUrlIsBlank() {
        ReflectionTestUtils.setField(webAppNotificationService, "webappUrl", "   ");

        webAppNotificationService.notifyDeploymentSuccess("myplugin", "main", "https://example.com/repo.git");

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("Should skip notification when webapp url is null")
    void shouldSkipNotificationWhenUrlIsNull() {
        ReflectionTestUtils.setField(webAppNotificationService, "webappUrl", null);

        webAppNotificationService.notifyDeploymentSuccess("myplugin", "main", "https://example.com/repo.git");

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("Should normalize trailing slashes off the webapp url")
    void shouldNormalizeTrailingSlashes() {
        ReflectionTestUtils.setField(webAppNotificationService, "webappUrl", "http://webapp:8080///");

        webAppNotificationService.notifyDeploymentSuccess("myplugin", "main", "https://example.com/repo.git");

        verify(restTemplate).postForEntity(
                eq("http://webapp:8080/api/deployment-history"), any(), eq(String.class));
    }

    @Test
    @DisplayName("Should build the deployment-history URL from the webapp url")
    void shouldBuildDeploymentHistoryUrl() {
        webAppNotificationService.notifyDeploymentSuccess("myplugin", "main", "https://example.com/repo.git");

        verify(restTemplate).postForEntity(
                eq("http://webapp:8080/api/deployment-history"), any(), eq(String.class));
    }

    @Test
    @DisplayName("Should set bearer auth header when token is configured")
    void shouldSetBearerAuthHeaderWhenTokenConfigured() {
        webAppNotificationService.notifyDeploymentSuccess("myplugin", "main", "https://example.com/repo.git");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        assertEquals("Bearer test-token", captor.getValue().getHeaders().getFirst("Authorization"));
    }

    @Test
    @DisplayName("Should omit auth header when token is blank")
    void shouldOmitAuthHeaderWhenTokenBlank() {
        ReflectionTestUtils.setField(webAppNotificationService, "deploymentAuthToken", "  ");

        webAppNotificationService.notifyDeploymentSuccess("myplugin", "main", "https://example.com/repo.git");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        assertNull(captor.getValue().getHeaders().getFirst("Authorization"));
    }

    @Test
    @DisplayName("Should omit auth header when token is null")
    void shouldOmitAuthHeaderWhenTokenNull() {
        ReflectionTestUtils.setField(webAppNotificationService, "deploymentAuthToken", null);

        webAppNotificationService.notifyDeploymentSuccess("myplugin", "main", "https://example.com/repo.git");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        assertNull(captor.getValue().getHeaders().getFirst("Authorization"));
    }

    @Test
    @DisplayName("Should not propagate RestTemplate exception on success notification")
    void shouldNotPropagateExceptionOnSuccessNotification() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertDoesNotThrow(() -> webAppNotificationService.notifyDeploymentSuccess(
                "myplugin", "main", "https://example.com/repo.git"));
    }

    @Test
    @DisplayName("Should not propagate RestTemplate exception on failure notification")
    void shouldNotPropagateExceptionOnFailureNotification() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertDoesNotThrow(() -> webAppNotificationService.notifyDeploymentFailure(
                "myplugin", "build failed", "main", "https://example.com/repo.git"));
    }

    @Test
    @DisplayName("Should include SUCCESS status and default message on deployment success")
    void shouldIncludeSuccessPayloadOnSuccess() {
        webAppNotificationService.notifyDeploymentSuccess("myplugin", "main", "https://example.com/repo.git");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) captor.getValue().getBody();
        assertNotNull(payload);
        assertEquals("myplugin", payload.get("pluginName"));
        assertEquals("SUCCESS", payload.get("status"));
        assertEquals("automated", payload.get("source"));
        assertEquals("main", payload.get("branch"));
        assertEquals("https://example.com/repo.git", payload.get("repoUrl"));
        assertEquals("Plugin deployed successfully", payload.get("message"));
    }

    @Test
    @DisplayName("Should include FAILURE status and reason as message on deployment failure")
    void shouldIncludeFailurePayloadOnFailure() {
        webAppNotificationService.notifyDeploymentFailure(
                "myplugin", "build failed", "main", "https://example.com/repo.git");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) captor.getValue().getBody();
        assertNotNull(payload);
        assertEquals("myplugin", payload.get("pluginName"));
        assertEquals("FAILURE", payload.get("status"));
        assertEquals("build failed", payload.get("message"));
    }

    @Test
    @DisplayName("Should omit branch, repoUrl and message from payload when null")
    void shouldOmitOptionalFieldsWhenNull() {
        webAppNotificationService.notifyDeploymentFailure("myplugin", null, null, null);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) captor.getValue().getBody();
        assertNotNull(payload);
        assertFalse(payload.containsKey("branch"));
        assertFalse(payload.containsKey("repoUrl"));
        assertFalse(payload.containsKey("message"));
    }
}
