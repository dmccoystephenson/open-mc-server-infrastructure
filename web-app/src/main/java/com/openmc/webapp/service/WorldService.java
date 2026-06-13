package com.openmc.webapp.service;

import com.openmc.webapp.dto.WorldInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class WorldService {

    private static final Logger log = LoggerFactory.getLogger(WorldService.class);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    @Value("${minecraft.server.world-directory:/mcserver/world}")
    private String worldDirectory;

    /**
     * Scan the server directory for world directories (siblings of the active world that contain
     * {@code level.dat}). Returns one entry per world sorted alphabetically, with the active world
     * marked.
     */
    public List<WorldInfo> listWorlds() {
        Path worldDir = Paths.get(worldDirectory).toAbsolutePath().normalize();
        Path serverDir = worldDir.getParent();

        if (serverDir == null || !Files.isDirectory(serverDir)) {
            log.warn("Server directory does not exist or is not a directory: {}", serverDir);
            return new ArrayList<>();
        }

        List<WorldInfo> result = new ArrayList<>();
        try (Stream<Path> entries = Files.list(serverDir)) {
            entries
                .filter(Files::isDirectory)
                .filter(p -> Files.exists(p.resolve("level.dat")))
                .sorted()
                .forEach(p -> {
                    String name = p.getFileName().toString();
                    boolean active = p.equals(worldDir);
                    long sizeMb = directorySize(p) / (1024 * 1024);
                    String lastModified = lastModified(p);
                    result.add(new WorldInfo(name, sizeMb, lastModified, active));
                });
        } catch (IOException e) {
            log.error("Failed to list worlds in {}: {}", serverDir, e.getMessage());
        }
        return result;
    }

    /**
     * Delete the named world directory. The active world and the parent server directory itself
     * are protected from deletion.
     *
     * @param name the directory name (e.g. {@code world.old})
     * @return human-readable result message (starts with "Error:" on failure)
     */
    public String deleteWorld(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Error: World name is required";
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            return "Error: Invalid world name";
        }

        Path worldDir = Paths.get(worldDirectory).toAbsolutePath().normalize();
        Path serverDir = worldDir.getParent();
        if (serverDir == null) {
            return "Error: Cannot resolve server directory";
        }

        Path target = serverDir.resolve(name).normalize();

        if (!target.getParent().equals(serverDir)) {
            return "Error: Invalid world path";
        }
        if (target.equals(worldDir)) {
            return "Error: Cannot delete the active world";
        }
        if (!Files.exists(target)) {
            return "Error: World does not exist: " + name;
        }
        if (!Files.isDirectory(target)) {
            return "Error: Not a directory: " + name;
        }
        if (!Files.exists(target.resolve("level.dat"))) {
            return "Error: Not a valid world directory (level.dat not found): " + name;
        }

        try {
            deleteDirectory(target);
            log.info("World deleted: {}", name);
            return "World deleted successfully: " + name;
        } catch (IOException e) {
            log.error("Failed to delete world {}: {}", name, e.getMessage());
            return "Error deleting world: " + name;
        }
    }

    private long directorySize(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0L; }
                    })
                    .sum();
        } catch (IOException e) {
            log.debug("Could not compute size of {}: {}", dir, e.getMessage());
            return 0L;
        }
    }

    private String lastModified(Path dir) {
        try {
            Instant t = Files.getLastModifiedTime(dir).toInstant();
            return ISO.format(t);
        } catch (IOException e) {
            return "unknown";
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
