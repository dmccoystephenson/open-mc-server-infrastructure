package com.openmc.backupmanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupTriggerResponse {

    @Schema(description = "Whether the backup operation succeeded")
    private boolean success;

    @Schema(description = "Human-readable result message")
    private String message;

    @Schema(description = "Path to the created backup directory")
    private String backupPath;
}
