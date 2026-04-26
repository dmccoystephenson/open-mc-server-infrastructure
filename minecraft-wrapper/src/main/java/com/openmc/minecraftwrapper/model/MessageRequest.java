package com.openmc.minecraftwrapper.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {
    @NotBlank
    @Schema(description = "The message text to send")
    private String text;

    @Schema(description = "Target destination for the message")
    private String destination;
}
