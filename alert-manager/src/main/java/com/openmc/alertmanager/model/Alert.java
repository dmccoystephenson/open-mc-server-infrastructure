package com.openmc.alertmanager.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents an alert to be sent to administrators or community members
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @NotBlank
    @Schema(description = "The title or subject of the alert")
    private String title;

    @NotBlank
    @Schema(description = "The detailed message content of the alert")
    private String message;

    @NotNull
    @Schema(description = "The severity level of the alert")
    private AlertLevel level;

    @Schema(description = "The source module that generated the alert")
    private String source;

    @Schema(description = "List of destinations where the alert should be sent")
    private List<AlertDestination> destinations;
}
