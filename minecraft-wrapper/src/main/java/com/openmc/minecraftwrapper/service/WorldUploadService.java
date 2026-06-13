package com.openmc.minecraftwrapper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class WorldUploadService {

    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 100_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 10L * 1024 * 1024 * 1024;
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    @Value("${world.directory:/mcserver/world}")
    private String worldDirectory;

    private final MinecraftServerService minecraftServerService;

    public WorldUploadService(MinecraftServerService minecraftServerService) {
        this.minecraftServerService = minecraftServerService;
    }

    /**
     * Stop the server (if running), replace the world directory with the contents of the
     * uploaded ZIP archive, then restart the server.
     *
     * <p>The ZIP may contain either the world folder itself (containing {@code level.dat})
     * as a single top-level directory, or the world contents directly at the root of the archive.
     * Both structures are handled automatically.
     *
     * @param file the ZIP archive to upload
     * @throws IllegalArgumentException if the file fails validation
     * @throws IOException              if extraction or directory manipulation fails
     */
    public void replaceWorld(MultipartFile file) throws IOException {
        validateFile(file);

        try (InputStream is = file.getInputStream()) {
            byte[] magic = is.readNBytes(4);
            if (!isZipMagic(magic)) {
                throw new IllegalArgumentException("Uploaded file is not a valid ZIP archive");
            }
        }

        Path worldDir = Paths.get(worldDirectory).toAbsolutePath().normalize();

        boolean wasRunning = false;
        try {
            minecraftServerService.stop();
            wasRunning = true;
            log.info("Server stopped for world replacement");
        } catch (IllegalStateException e) {
            log.info("Server was not running before world upload; proceeding without stop");
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("world-upload-");

            try (InputStream is = file.getInputStream();
                 ZipInputStream zis = new ZipInputStream(is)) {
                extractZip(zis, tempDir);
            }

            Path worldRoot = detectWorldRoot(tempDir);

            if (Files.exists(worldDir)) {
                deleteDirectory(worldDir);
            }
            if (worldDir.getParent() != null) {
                Files.createDirectories(worldDir.getParent());
            }
            Files.move(worldRoot, worldDir, StandardCopyOption.REPLACE_EXISTING);

            log.info("World replaced successfully at {}", worldDir);
        } finally {
            if (tempDir != null) {
                try {
                    if (Files.exists(tempDir)) {
                        deleteDirectory(tempDir);
                    }
                } catch (IOException e) {
                    log.debug("Could not clean up temp directory {}: {}", tempDir, e.getMessage());
                }
            }
            if (wasRunning) {
                try {
                    minecraftServerService.start();
                    log.info("Server restarted after world upload");
                } catch (Exception e) {
                    log.error("Failed to restart server after world upload", e);
                }
            }
        }
    }

    /**
     * If the extracted archive has a single top-level directory containing {@code level.dat},
     * treat that directory as the world root. Otherwise treat the extraction directory itself
     * as the world root (the archive contained world contents at the top level).
     */
    Path detectWorldRoot(Path extractDir) throws IOException {
        try (Stream<Path> entries = Files.list(extractDir)) {
            List<Path> topLevel = entries
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> !p.getFileName().toString().equals("__MACOSX"))
                    .toList();
            if (topLevel.size() == 1
                    && Files.isDirectory(topLevel.get(0))
                    && Files.exists(topLevel.get(0).resolve("level.dat"))) {
                return topLevel.get(0);
            }
        }
        return extractDir;
    }

    private void extractZip(ZipInputStream zis, Path targetDir) throws IOException {
        ZipEntry entry;
        int entryCount = 0;
        long totalBytes = 0;

        while ((entry = zis.getNextEntry()) != null) {
            if (++entryCount > MAX_ZIP_ENTRIES) {
                throw new IllegalArgumentException("Archive has too many entries (zip bomb protection)");
            }

            String name = entry.getName();
            if (name.startsWith("__MACOSX/")) {
                zis.closeEntry();
                continue;
            }

            Path target = targetDir.resolve(name).normalize();
            if (!target.startsWith(targetDir)) {
                throw new IllegalArgumentException("Archive contains a path traversal entry: " + name);
            }

            if (entry.isDirectory()) {
                Files.createDirectories(target);
            } else {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                long written = Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                totalBytes += written;
                if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                    throw new IllegalArgumentException(
                            "Archive uncompressed size exceeds the 10 GB limit (zip bomb protection)");
                }
            }
            zis.closeEntry();
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
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the 2 GB limit");
        }
    }

    private boolean isZipMagic(byte[] bytes) {
        if (bytes.length < ZIP_MAGIC.length) return false;
        for (int i = 0; i < ZIP_MAGIC.length; i++) {
            if (bytes[i] != ZIP_MAGIC[i]) return false;
        }
        return true;
    }
}
