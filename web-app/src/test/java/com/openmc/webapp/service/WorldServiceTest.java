package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.dto.WorldInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorldService Tests")
class WorldServiceTest {

    @TempDir
    Path serverDir;

    private WorldService worldService;
    private Path activeWorldDir;

    @BeforeEach
    void setUp() throws IOException {
        activeWorldDir = serverDir.resolve("world");
        Files.createDirectory(activeWorldDir);
        Files.createFile(activeWorldDir.resolve("level.dat"));

        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setWorldDirectory(activeWorldDir.toString());
        worldService = new WorldService(serverConfig);
    }

    // ── listWorlds ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listWorlds returns only directories containing level.dat")
    void listWorldsReturnsOnlyValidWorlds() throws IOException {
        Path oldWorld = serverDir.resolve("world_old");
        Files.createDirectory(oldWorld);
        Files.createFile(oldWorld.resolve("level.dat"));

        Path noLevelDat = serverDir.resolve("not_a_world");
        Files.createDirectory(noLevelDat);

        List<WorldInfo> worlds = worldService.listWorlds();

        assertEquals(2, worlds.size());
        assertTrue(worlds.stream().anyMatch(w -> w.getName().equals("world")));
        assertTrue(worlds.stream().anyMatch(w -> w.getName().equals("world_old")));
        assertFalse(worlds.stream().anyMatch(w -> w.getName().equals("not_a_world")));
    }

    @Test
    @DisplayName("listWorlds marks the configured world directory as active")
    void listWorldsMarksActiveWorld() throws IOException {
        Path oldWorld = serverDir.resolve("world_old");
        Files.createDirectory(oldWorld);
        Files.createFile(oldWorld.resolve("level.dat"));

        List<WorldInfo> worlds = worldService.listWorlds();

        WorldInfo active = worlds.stream().filter(w -> w.getName().equals("world")).findFirst().orElseThrow();
        WorldInfo inactive = worlds.stream().filter(w -> w.getName().equals("world_old")).findFirst().orElseThrow();

        assertTrue(active.isActive());
        assertFalse(inactive.isActive());
    }

    @Test
    @DisplayName("listWorlds returns empty list when server directory does not exist")
    void listWorldsReturnsEmptyWhenServerDirMissing() {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setWorldDirectory("/nonexistent/path/world");
        WorldService service = new WorldService(serverConfig);

        List<WorldInfo> worlds = service.listWorlds();

        assertTrue(worlds.isEmpty());
    }

    @Test
    @DisplayName("listWorlds returns results sorted alphabetically")
    void listWorldsReturnsSortedResults() throws IOException {
        Files.createDirectory(serverDir.resolve("zebra_world"));
        Files.createFile(serverDir.resolve("zebra_world/level.dat"));
        Files.createDirectory(serverDir.resolve("alpha_world"));
        Files.createFile(serverDir.resolve("alpha_world/level.dat"));

        List<WorldInfo> worlds = worldService.listWorlds();

        List<String> names = worlds.stream().map(WorldInfo::getName).toList();
        assertEquals(List.of("alpha_world", "world", "zebra_world"), names);
    }

    // ── deleteWorld — input validation ───────────────────────────────────────

    @Test
    @DisplayName("deleteWorld rejects null name")
    void deleteWorldRejectsNull() {
        String result = worldService.deleteWorld(null);
        assertTrue(result.startsWith("Error:"), "Expected error but got: " + result);
    }

    @Test
    @DisplayName("deleteWorld rejects empty name")
    void deleteWorldRejectsEmpty() {
        String result = worldService.deleteWorld("  ");
        assertTrue(result.startsWith("Error:"), "Expected error but got: " + result);
    }

    @Test
    @DisplayName("deleteWorld rejects name containing forward slash")
    void deleteWorldRejectsForwardSlash() {
        String result = worldService.deleteWorld("some/path");
        assertTrue(result.startsWith("Error:"), "Expected error but got: " + result);
    }

    @Test
    @DisplayName("deleteWorld rejects name containing backslash")
    void deleteWorldRejectsBackslash() {
        String result = worldService.deleteWorld("some\\path");
        assertTrue(result.startsWith("Error:"), "Expected error but got: " + result);
    }

    @Test
    @DisplayName("deleteWorld rejects name containing double dot")
    void deleteWorldRejectsDoubleDot() {
        String result = worldService.deleteWorld("../escape");
        assertTrue(result.startsWith("Error:"), "Expected error but got: " + result);
    }

    // ── deleteWorld — business guards ────────────────────────────────────────

    @Test
    @DisplayName("deleteWorld rejects the active world")
    void deleteWorldRejectsActiveWorld() {
        String result = worldService.deleteWorld("world");
        assertTrue(result.startsWith("Error:"), "Expected error but got: " + result);
        assertTrue(Files.exists(activeWorldDir), "Active world directory should not be deleted");
    }

    @Test
    @DisplayName("deleteWorld rejects a directory with no level.dat")
    void deleteWorldRejectsDirectoryWithoutLevelDat() throws IOException {
        Path noLevelDat = serverDir.resolve("world_nolevel");
        Files.createDirectory(noLevelDat);

        String result = worldService.deleteWorld("world_nolevel");

        assertTrue(result.startsWith("Error:"), "Expected error but got: " + result);
        assertTrue(Files.exists(noLevelDat), "Directory should not be deleted");
    }

    @Test
    @DisplayName("deleteWorld rejects a nonexistent world name")
    void deleteWorldRejectsNonexistent() {
        String result = worldService.deleteWorld("does_not_exist");
        assertTrue(result.startsWith("Error:"), "Expected error but got: " + result);
    }

    // ── deleteWorld — success path ────────────────────────────────────────────

    @Test
    @DisplayName("deleteWorld removes a valid non-active world directory")
    void deleteWorldSucceeds() throws IOException {
        Path oldWorld = serverDir.resolve("world_old");
        Files.createDirectory(oldWorld);
        Files.createFile(oldWorld.resolve("level.dat"));

        String result = worldService.deleteWorld("world_old");

        assertFalse(result.startsWith("Error:"), "Expected success but got: " + result);
        assertFalse(Files.exists(oldWorld), "World directory should have been deleted");
    }

    @Test
    @DisplayName("deleteWorld recursively removes nested files and directories")
    void deleteWorldRemovesNestedContent() throws IOException {
        Path oldWorld = serverDir.resolve("world_old");
        Files.createDirectory(oldWorld);
        Files.createFile(oldWorld.resolve("level.dat"));
        Path regionDir = oldWorld.resolve("region");
        Files.createDirectory(regionDir);
        Files.createFile(regionDir.resolve("r.0.0.mca"));
        Files.createFile(regionDir.resolve("r.0.1.mca"));

        String result = worldService.deleteWorld("world_old");

        assertFalse(result.startsWith("Error:"), "Expected success but got: " + result);
        assertFalse(Files.exists(oldWorld), "World directory and all contents should be deleted");
    }

    @Test
    @DisplayName("deleteWorld leaves the active world untouched after deleting another")
    void deleteWorldLeavesActiveWorldIntact() throws IOException {
        Path oldWorld = serverDir.resolve("world_old");
        Files.createDirectory(oldWorld);
        Files.createFile(oldWorld.resolve("level.dat"));

        worldService.deleteWorld("world_old");

        assertTrue(Files.exists(activeWorldDir), "Active world must remain after deleting another");
        assertTrue(Files.exists(activeWorldDir.resolve("level.dat")), "Active world level.dat must remain");
    }
}
