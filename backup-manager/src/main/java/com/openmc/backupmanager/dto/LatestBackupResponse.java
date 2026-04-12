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
public class LatestBackupResponse {

    @Schema(description = "Whether a backup record is available")
    private boolean available;

    @Schema(description = "Whether the latest backup was successful")
    private Boolean success;

    @Schema(description = "Timestamp of the latest backup")
    private String timestamp;

    @Schema(description = "Human-readable status message")
    private String message;

    @Schema(description = "Path to the backup directory")
    private String backupPath;
}
