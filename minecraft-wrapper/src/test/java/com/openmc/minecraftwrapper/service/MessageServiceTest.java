package com.openmc.minecraftwrapper.service;

import com.openmc.minecraftwrapper.exception.MessageDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * These exercise the actual JSON that leaves the wrapper, not just the Java
 * object it was built from. The alert manager validates the request body it
 * receives, so a test that only inspects the {@code Alert} bean can pass while
 * the wire request is rejected — which is exactly what happened before.
 */
@DisplayName("MessageService Tests")
class MessageServiceTest {

    private static final String ALERT_URL = "http://test:8090/api/alerts";

    private RestTemplate restTemplate;
    private MockRestServiceServer alertManager;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        alertManager = MockRestServiceServer.createServer(restTemplate);
        messageService = new MessageService(restTemplate);
        ReflectionTestUtils.setField(messageService, "alertManagerUrl", ALERT_URL);
    }

    @Test
    @DisplayName("Should forward an alert the alert manager will accept")
    void shouldForwardAnAlertTheAlertManagerWillAccept() {
        // The alert manager's Alert model marks title @NotBlank and message
        // @NotBlank, and maps level onto its AlertLevel enum. An alert missing
        // any of those is rejected with 400 before it is ever delivered.
        alertManager.expect(requestTo(ALERT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.message").value("Test message"))
                .andExpect(jsonPath("$.level").value("INFO"))
                .andExpect(jsonPath("$.source").value("minecraft-server"))
                .andExpect(jsonPath("$.destinations[0]").value("MINECRAFT"))
                .andRespond(withSuccess("Alert sent successfully", MediaType.TEXT_PLAIN));

        messageService.sendMessage("Test message", "MINECRAFT");

        alertManager.verify();
    }

    @Test
    @DisplayName("Should default to the MINECRAFT destination")
    void shouldDefaultToMinecraftDestination() {
        alertManager.expect(requestTo(ALERT_URL))
                .andExpect(jsonPath("$.destinations[0]").value("MINECRAFT"))
                .andRespond(withSuccess());

        messageService.sendMessage("Test message");

        alertManager.verify();
    }

    @Test
    @DisplayName("Should convert the destination to uppercase")
    void shouldConvertDestinationToUppercase() {
        alertManager.expect(requestTo(ALERT_URL))
                .andExpect(jsonPath("$.destinations[0]").value("DISCORD"))
                .andRespond(withSuccess());

        messageService.sendMessage("Test", "discord");

        alertManager.verify();
    }

    @Test
    @DisplayName("Should report a rejection from the alert manager rather than swallowing it")
    void shouldReportRejectionFromAlertManager() {
        alertManager.expect(requestTo(ALERT_URL))
                .andRespond(withBadRequest()
                        .body("{\"status\":400,\"message\":\"Validation failed\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        MessageDeliveryException thrown = assertThrows(MessageDeliveryException.class,
                () -> messageService.sendMessage("Test message", "MINECRAFT"));

        assertTrue(thrown.getMessage().contains("400"),
                "the caller should be told what the alert manager said: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("not delivered"),
                "the caller should be told the message was not delivered: " + thrown.getMessage());
        alertManager.verify();
    }

    @Test
    @DisplayName("Should report an unreachable alert manager rather than swallowing it")
    void shouldReportUnreachableAlertManager() {
        alertManager.expect(requestTo(ALERT_URL))
                .andRespond(withException(new IOException("Connection refused")));

        MessageDeliveryException thrown = assertThrows(MessageDeliveryException.class,
                () -> messageService.sendMessage("Test message", "MINECRAFT"));

        assertTrue(thrown.getMessage().contains(ALERT_URL),
                "the error should name the endpoint that could not be reached: " + thrown.getMessage());
        alertManager.verify();
    }

    @Test
    @DisplayName("Should report an unconfigured alert manager without attempting a call")
    void shouldReportUnconfiguredAlertManager() {
        ReflectionTestUtils.setField(messageService, "alertManagerUrl", "  ");

        MessageDeliveryException thrown = assertThrows(MessageDeliveryException.class,
                () -> messageService.sendMessage("Test message", "MINECRAFT"));

        assertTrue(thrown.getMessage().contains("alert.manager.url"),
                "the error should name the setting to fix: " + thrown.getMessage());
        alertManager.verify();
    }

    @Test
    @DisplayName("Should reject a destination the alert manager does not know, before calling it")
    void shouldRejectUnknownDestination() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> messageService.sendMessage("Test message", "SLACK"));

        assertTrue(thrown.getMessage().contains("MINECRAFT"),
                "the error should list the destinations that do work: " + thrown.getMessage());
        alertManager.verify();
    }

    @Test
    @DisplayName("Should reject blank message text before calling the alert manager")
    void shouldRejectBlankText() {
        assertThrows(IllegalArgumentException.class, () -> messageService.sendMessage("   ", "MINECRAFT"));
        alertManager.verify();
    }
}
