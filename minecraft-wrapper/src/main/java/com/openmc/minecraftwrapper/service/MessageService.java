package com.openmc.minecraftwrapper.service;

import com.openmc.minecraftwrapper.model.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Slf4j
@Service
public class MessageService {

    @Value("${alert.manager.url:http://alert-manager:8090/api/alerts}")
    private String alertManagerUrl;

    private final RestTemplate restTemplate;

    public MessageService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendMessage(String text, String destination) {
        try {
            log.info("Sending message to {}: {}", alertManagerUrl, text);

            Alert alert = Alert.builder()
                    .message(text)
                    .destinations(Collections.singletonList(destination.toUpperCase()))
                    .source("minecraft-server")
                    .level("INFO")
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Alert> request = new HttpEntity<>(alert, headers);

            restTemplate.postForEntity(alertManagerUrl, request, String.class);
            log.info("Message sent successfully");
        } catch (Exception e) {
            log.error("Failed to send message: {}", e.getMessage(), e);
        }
    }

    public void sendMessage(String text) {
        sendMessage(text, "MINECRAFT");
    }
}
