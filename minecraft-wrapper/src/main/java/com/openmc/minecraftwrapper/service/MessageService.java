package com.openmc.minecraftwrapper.service;

import com.openmc.minecraftwrapper.exception.MessageDeliveryException;
import com.openmc.minecraftwrapper.model.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Forwards player-facing messages to the alert manager, which is the service
 * that actually reaches Discord and the game console.
 *
 * <p>This forward is synchronous and its outcome is reported to the caller.
 * A message that the alert manager refuses, or that never reaches it, raises
 * {@link MessageDeliveryException} rather than being logged and forgotten.</p>
 */
@Slf4j
@Service
public class MessageService {

    /**
     * Title attached to every forwarded message.
     *
     * <p>The alert manager's own {@code Alert} model annotates {@code title}
     * with {@code @NotBlank}, so an alert without one is rejected outright with
     * HTTP 400 and nothing is delivered. The title is not shown in game — the
     * MINECRAFT destination broadcasts only the message body over RCON — but it
     * labels the entry in Discord and in {@code GET /api/alerts} history.</p>
     */
    static final String ALERT_TITLE = "Server Message";

    /** Source label recorded on the alert, matching the wrapper's other alerts. */
    static final String ALERT_SOURCE = "minecraft-server";

    /**
     * Severity forwarded for player messages. These are broadcasts, not
     * incidents, so they are always informational. The value must be the name
     * of a constant in the alert manager's {@code AlertLevel} enum; anything
     * else fails to deserialise there.
     */
    static final String ALERT_LEVEL = "INFO";

    /** Destination used when the caller does not name one. */
    static final String DEFAULT_DESTINATION = "MINECRAFT";

    /**
     * The destinations the alert manager's {@code AlertDestination} enum knows.
     * An unrecognised value fails to deserialise there and comes back as an
     * opaque "Malformed request body", so it is worth rejecting here where the
     * error can name the problem.
     */
    private static final List<String> SUPPORTED_DESTINATIONS = List.of("DISCORD", "MINECRAFT");

    @Value("${alert.manager.url:http://alert-manager:8090/api/alerts}")
    private String alertManagerUrl;

    private final RestTemplate restTemplate;

    public MessageService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Forward a message to the alert manager for delivery.
     *
     * @param text        the message body; must not be blank
     * @param destination {@code MINECRAFT} or {@code DISCORD}, case-insensitive;
     *                    blank or null means {@link #DEFAULT_DESTINATION}
     * @throws IllegalArgumentException  if the text is blank or the destination
     *                                   is not one the alert manager accepts
     * @throws MessageDeliveryException  if the alert manager refused the message
     *                                   or could not be reached
     */
    public void sendMessage(String text, String destination) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Message text must not be blank.");
        }

        String target = normaliseDestination(destination);

        if (alertManagerUrl == null || alertManagerUrl.isBlank()) {
            throw new MessageDeliveryException(
                    "The message was not delivered: no alert manager is configured. "
                            + "Set alert.manager.url (ALERT_MANAGER_URL) to the alert manager's "
                            + "/api/alerts endpoint.");
        }

        Alert alert = Alert.builder()
                .title(ALERT_TITLE)
                .message(text)
                .level(ALERT_LEVEL)
                .source(ALERT_SOURCE)
                .destinations(Collections.singletonList(target))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Alert> request = new HttpEntity<>(alert, headers);

        log.info("Forwarding message to {} for destination {}", alertManagerUrl, target);

        try {
            restTemplate.postForEntity(alertManagerUrl, request, String.class);
        } catch (RestClientResponseException e) {
            throw new MessageDeliveryException(String.format(
                    "The message was not delivered: the alert manager refused it with HTTP %d. "
                            + "Check the alert-manager log for the reason it gave: %s",
                    e.getStatusCode().value(), summarise(e.getResponseBodyAsString())), e);
        } catch (RestClientException e) {
            throw new MessageDeliveryException(String.format(
                    "The message was not delivered: the alert manager at %s could not be reached. "
                            + "Check that the service is running and that alert.manager.url points at "
                            + "it. Cause: %s",
                    alertManagerUrl, e.getMessage()), e);
        }

        log.info("Alert manager accepted the message");
    }

    /**
     * Forward a message to the default destination.
     *
     * @see #sendMessage(String, String)
     */
    public void sendMessage(String text) {
        sendMessage(text, DEFAULT_DESTINATION);
    }

    private static String normaliseDestination(String destination) {
        String target = (destination == null || destination.isBlank())
                ? DEFAULT_DESTINATION
                : destination.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_DESTINATIONS.contains(target)) {
            throw new IllegalArgumentException(String.format(
                    "Unknown message destination '%s'. Use one of: %s.",
                    destination, String.join(", ", SUPPORTED_DESTINATIONS)));
        }
        return target;
    }

    /** Keep a downstream error body short enough to be readable in a response. */
    private static String summarise(String body) {
        if (body == null || body.isBlank()) {
            return "(no response body)";
        }
        String trimmed = body.strip();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "...";
    }
}
