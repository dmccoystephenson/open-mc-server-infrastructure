package com.openmc.minecraftwrapper.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Performance metrics snapshot for the Minecraft server.
 *
 * <p>All numeric values are best-effort: fields that cannot be determined on the
 * current platform or when the server is not running are left {@code null}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerMetrics {

    // ── JVM heap (the wrapper Spring Boot process) ──────────────────────────────
    /** Heap memory currently used by the wrapper JVM, in MiB. */
    @Schema(description = "Heap memory currently used by the wrapper JVM, in MiB")
    private Long wrapperHeapUsedMb;

    /** Maximum heap available to the wrapper JVM, in MiB. */
    @Schema(description = "Maximum heap available to the wrapper JVM, in MiB")
    private Long wrapperHeapMaxMb;

    /** Percentage of the wrapper JVM max heap that is currently used. */
    @Schema(description = "Percentage of the wrapper JVM max heap that is currently used")
    private Double wrapperHeapUsedPercent;

    // ── Minecraft server process ────────────────────────────────────────────────
    /** Resident Set Size of the Minecraft server process, in MiB (Linux only). */
    @Schema(description = "Resident Set Size of the Minecraft server process, in MiB (Linux only)")
    private Long serverMemoryMb;

    /** Wall-clock seconds the server process has been running. */
    @Schema(description = "Wall-clock seconds the server process has been running")
    private Long serverUptimeSeconds;

    // ── Ticks per second ────────────────────────────────────────────────────────
    /**
     * Most recent TPS reading extracted from {@code logs/latest.log}.
     * Only populated for Paper/Spigot servers that log
     * {@code TPS from last 1m, 5m, 15m: X, X, X}.
     */
    @Schema(description = "Most recent TPS reading extracted from logs/latest.log")
    private String tps;

    /**
     * Human-readable note explaining why {@code tps} is {@code null}, when
     * applicable (e.g. log file missing, no TPS line found, server not running).
     */
    @Schema(description = "Human-readable note explaining why tps is null, when applicable")
    private String tpsNote;
}
