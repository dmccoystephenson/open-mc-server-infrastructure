package com.openmc.minecraftwrapper.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    @Schema(description = "The alert title")
    private String title;

    @Schema(description = "The alert message content")
    private String message;

    @Schema(description = "The severity level")
    private String level;

    @Schema(description = "The source module")
    private String source;

    @Schema(description = "Target destinations for the alert")
    private List<String> destinations;
}
