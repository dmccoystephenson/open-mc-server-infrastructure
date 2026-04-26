package com.openmc.alertmanager.model;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "The alert title")
    private String title;

    @Schema(description = "The alert message")
    private String message;

    @Schema(description = "The severity level")
    private AlertLevel level;

    @Schema(description = "The source module")
    private String source;

    @Schema(description = "When the alert was received")
    private Instant receivedAt;
}
