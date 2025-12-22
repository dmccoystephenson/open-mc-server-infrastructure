package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

@Service
public class PluginService {
    
    private static final Logger logger = LoggerFactory.getLogger(PluginService.class);
    
    private final ServerConfig serverConfig;
    
    public PluginService(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }
    
    /**
     * List all plugin files in the plugins directory
     * @return List of plugin filenames
     */
    public List<String> listPlugins() {
        String pluginsDir = serverConfig.getPluginsDirectory();
        Path pluginsPath = Paths.get(pluginsDir);
        
        if (!Files.exists(pluginsPath)) {
            logger.warn("Plugins directory does not exist: {}", pluginsDir);
            return new ArrayList<>();
        }
        
        if (!Files.isDirectory(pluginsPath)) {
            logger.error("Plugins path is not a directory: {}", pluginsDir);
            return new ArrayList<>();
        }
        
        try (Stream<Path> files = Files.list(pluginsPath)) {
            return files
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith(".jar"))
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error listing plugins in directory: {}", pluginsDir, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Upload a plugin file to the plugins directory
     * @param file The plugin file to upload
     * @return Success message or error message
     */
    public String uploadPlugin(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "Error: No file provided";
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".jar")) {
            return "Error: File must be a .jar file";
        }
        
        // Reject any filename containing path separators to prevent traversal
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return "Error: Invalid filename";
        }
        
        // Read file bytes once to avoid multiple InputStream reads
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            logger.error("Error reading file bytes: {}", filename, e);
            return "Error: Unable to read file";
        }
        
        // Validate JAR file structure using the byte array
        if (!isValidJarFile(fileBytes)) {
            return "Error: File is not a valid JAR file";
        }
        
        // Sanitize filename to prevent directory traversal
        filename = new File(filename).getName();
        
        String pluginsDir = serverConfig.getPluginsDirectory();
        Path pluginsPath = Paths.get(pluginsDir);
        
        try {
            // Create plugins directory if it doesn't exist
            if (!Files.exists(pluginsPath)) {
                Files.createDirectories(pluginsPath);
                logger.info("Created plugins directory: {}", pluginsDir);
            }
            
            // Normalize paths and ensure the target is directly within the plugins directory
            Path pluginsPathNormalized = pluginsPath.toAbsolutePath().normalize();
            Path targetPath = pluginsPathNormalized.resolve(filename);
            Path targetPathNormalized = targetPath.normalize();
            
            if (!pluginsPathNormalized.equals(targetPathNormalized.getParent())) {
                logger.warn("Attempted plugin upload with invalid target path: {}", targetPathNormalized);
                return "Error: Invalid plugin path";
            }
            
            // Check if file already exists
            if (Files.exists(targetPathNormalized)) {
                return "Error: Plugin file already exists. Please delete it first.";
            }
            
            // Save the file using the byte array
            Files.write(targetPathNormalized, fileBytes);
            logger.info("Plugin uploaded successfully: {}", filename);
            return "Plugin uploaded successfully: " + filename;
            
        } catch (IOException e) {
            logger.error("Error uploading plugin: {}", filename, e);
            return "Error uploading plugin: " + filename;
        }
    }
    
    /**
     * Validates that the uploaded file is a valid JAR file by checking its structure
     * @param fileBytes The file bytes to validate
     * @return true if valid JAR file, false otherwise
     */
    private boolean isValidJarFile(byte[] fileBytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
             ZipInputStream zis = new ZipInputStream(bais)) {
            // Check that it's a valid ZIP and look for JAR-specific structure
            java.util.zip.ZipEntry entry;
            boolean hasManifest = false;
            
            while ((entry = zis.getNextEntry()) != null) {
                // Check for META-INF/MANIFEST.MF which is required for JAR files
                if ("META-INF/MANIFEST.MF".equals(entry.getName())) {
                    hasManifest = true;
                    break;
                }
            }
            
            return hasManifest;
        } catch (Exception e) {
            logger.warn("File validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Delete a plugin file from the plugins directory
     * @param filename The name of the plugin file to delete
     * @return Success message or error message
     */
    public String deletePlugin(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "Error: Filename is required";
        }
        
        if (!filename.endsWith(".jar")) {
            return "Error: File must be a .jar file";
        }
        
        // Reject any filename containing path separators to prevent traversal
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return "Error: Invalid filename";
        }
        
        String pluginsDir = serverConfig.getPluginsDirectory();
        
        try {
            // Resolve and normalize paths to prevent directory traversal
            Path pluginsDirPath = Paths.get(pluginsDir).toAbsolutePath().normalize();
            Path pluginPath = pluginsDirPath.resolve(filename).normalize();
            
            // Ensure the file is directly in the plugins directory (not in a subdirectory)
            if (!pluginPath.getParent().equals(pluginsDirPath)) {
                return "Error: Invalid file path";
            }
            
            String resolvedFilename = pluginPath.getFileName().toString();
            
            if (!Files.exists(pluginPath)) {
                return "Error: Plugin file does not exist: " + resolvedFilename;
            }
            
            if (!Files.isRegularFile(pluginPath)) {
                return "Error: Not a regular file: " + resolvedFilename;
            }
            
            Files.delete(pluginPath);
            logger.info("Plugin deleted successfully: {}", resolvedFilename);
            return "Plugin deleted successfully: " + resolvedFilename;
            
        } catch (IOException e) {
            logger.error("Error deleting plugin: {}", filename, e);
            return "Error deleting plugin: " + filename;
        }
    }
}
