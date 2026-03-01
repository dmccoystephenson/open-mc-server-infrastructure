package com.openmc.alertmanager.service;

import com.openmc.alertmanager.model.Alert;
import com.openmc.alertmanager.model.AlertDestination;
import com.openmc.alertmanager.model.AlertRecord;
import com.openmc.alertmanager.repository.AlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Main alert service that coordinates sending alerts to various destinations
 */
@Service
@Slf4j
public class AlertService {

    private final DiscordAlertService discordAlertService;
    private final MinecraftMessageService minecraftMessageService;
    private final AlertRepository alertRepository;

    public AlertService(DiscordAlertService discordAlertService,
                       MinecraftMessageService minecraftMessageService,
                       AlertRepository alertRepository) {
        this.discordAlertService = discordAlertService;
        this.minecraftMessageService = minecraftMessageService;
        this.alertRepository = alertRepository;
    }

    /**
     * Return the most recent alerts, newest first.
     *
     * @param limit maximum number of records to return (capped at 100)
     * @return list of recent alert records
     */
    public List<AlertRecord> getRecentAlerts(int limit) {
        return alertRepository.getRecent(Math.min(limit, AlertRepository.MAX_STORED_ALERTS));
    }

    /**
     * Send an alert to specified destinations, or all configured destinations if none specified
     *
     * @param alert The alert to send
     */
    public void sendAlert(Alert alert) {
        log.info("Processing alert: {} from source: {}", alert.getTitle(), alert.getSource());

        // Store in history for later retrieval
        alertRepository.store(alert);

        // Determine destinations - if not specified, send to all
        List<AlertDestination> destinations = alert.getDestinations();
        if (destinations == null || destinations.isEmpty()) {
            destinations = Arrays.asList(AlertDestination.values());
            log.debug("No destinations specified, sending to all: {}", destinations);
        }
        
        // Send to each requested destination
        for (AlertDestination destination : destinations) {
            try {
                switch (destination) {
                    case DISCORD:
                        discordAlertService.sendAlert(alert);
                        break;
                    case MINECRAFT:
                        // For Minecraft, use the message field as the text to send
                        minecraftMessageService.sendMessage(alert.getMessage());
                        break;
                    default:
                        log.warn("Unknown destination: {}", destination);
                }
            } catch (Exception e) {
                log.error("Failed to send alert to destination: {}", destination, e);
                // Continue to other destinations even if one fails
            }
        }
    }
}
