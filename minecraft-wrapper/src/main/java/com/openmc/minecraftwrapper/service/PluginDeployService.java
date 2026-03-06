package com.openmc.minecraftwrapper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class PluginDeployService {

    // Maximum file size for plugin upload (100MB) to prevent memory issues
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    @Value("${plugins.directory:/mcserver/plugins}")
    private String pluginsDirectory;

    /**
     * Replace an existing plugin JAR with the provided file.
     *
     * @param pluginName the filename of the plugin to replace (e.g. {@code MyPlugin.jar})
     * @param file       the new JAR file to deploy
     * @throws IllegalArgumentException if {@code pluginName} or {@code file} fails validation
     * @throws IOException              if the file cannot be written to the plugins directory
     */
    public void replacePlugin(String pluginName, MultipartFile file) throws IOException {
        validatePluginName(pluginName);
        validateFile(file);

        // Sanitize filename to prevent directory traversal
        String sanitizedName = new File(pluginName).getName();

        Path pluginsDirPath = Paths.get(pluginsDirectory).toAbsolutePath().normalize();
        Path targetPath = pluginsDirPath.resolve(sanitizedName).normalize();

        // Ensure the resolved target stays within the plugins directory
        if (!targetPath.getParent().equals(pluginsDirPath)) {
            log.warn("Rejected plugin deploy with out-of-bounds target path: {}", targetPath);
            throw new IllegalArgumentException("Invalid plugin name");
        }

        if (!Files.exists(pluginsDirPath)) {
            throw new IOException("Plugins directory does not exist: " + pluginsDirectory);
        }

        byte[] fileBytes = file.getBytes();

        if (!isValidJarFile(fileBytes)) {
            throw new IllegalArgumentException("Uploaded file is not a valid JAR");
        }

        // Write to a temp file first, then atomically move into place
        Path tempPath = pluginsDirPath.resolve(sanitizedName + ".tmp");
        try {
            Files.write(tempPath, fileBytes);
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Plugin deployed successfully: {}", sanitizedName);
        } catch (IOException e) {
            // Clean up temp file if the move failed
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
                log.debug("Could not delete temp file: {}", tempPath);
            }
            throw e;
        }
    }

    private void validatePluginName(String pluginName) {
        if (pluginName == null || pluginName.trim().isEmpty()) {
            throw new IllegalArgumentException("Plugin name must not be empty");
        }
        if (!pluginName.endsWith(".jar")) {
            throw new IllegalArgumentException("Plugin name must end with .jar");
        }
        if (pluginName.contains("/") || pluginName.contains("\\") || pluginName.contains("..")) {
            throw new IllegalArgumentException("Plugin name must not contain path separators or '..'");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            log.warn("Plugin deploy rejected: file too large ({} bytes)", file.getSize());
            throw new IllegalArgumentException("File size exceeds the 100 MB limit");
        }
    }

    /**
     * Validate that the byte array is a JAR file (i.e. a ZIP archive containing
     * {@code META-INF/MANIFEST.MF}).
     */
    private boolean isValidJarFile(byte[] fileBytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
             ZipInputStream zis = new ZipInputStream(bais)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("META-INF/MANIFEST.MF".equals(entry.getName())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("JAR file validation failed: {}", e.getMessage());
            return false;
        }
    }
}
