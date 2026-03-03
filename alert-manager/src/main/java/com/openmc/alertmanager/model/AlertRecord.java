package com.openmc.alertmanager.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a stored alert with its receipt timestamp.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRecord {

    private String title;
    private String message;
    private AlertLevel level;
    private String source;
    private Instant receivedAt;
}
