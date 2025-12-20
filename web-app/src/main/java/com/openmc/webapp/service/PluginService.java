package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
        
        // Validate JAR file structure
        if (!isValidJarFile(file)) {
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
            
            Path targetPath = pluginsPath.resolve(filename);
            
            // Check if file already exists
            if (Files.exists(targetPath)) {
                return "Error: Plugin file already exists. Please delete it first.";
            }
            
            // Save the file
            Files.copy(file.getInputStream(), targetPath);
            logger.info("Plugin uploaded successfully: {}", filename);
            return "Plugin uploaded successfully: " + filename;
            
        } catch (IOException e) {
            logger.error("Error uploading plugin: {}", filename, e);
            return "Error uploading plugin: " + e.getMessage();
        }
    }
    
    /**
     * Validates that the uploaded file is a valid JAR file by checking its structure
     * @param file The file to validate
     * @return true if valid JAR file, false otherwise
     */
    private boolean isValidJarFile(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {
            // Try to read the first entry - if it fails, it's not a valid ZIP/JAR
            return zis.getNextEntry() != null;
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
        
        String pluginsDir = serverConfig.getPluginsDirectory();
        
        try {
            // Resolve and normalize paths to prevent directory traversal
            Path pluginsDirPath = Paths.get(pluginsDir).toAbsolutePath().normalize();
            Path pluginPath = pluginsDirPath.resolve(filename).normalize();
            
            // Ensure the resolved path is within the plugins directory
            if (!pluginPath.startsWith(pluginsDirPath)) {
                return "Error: Invalid file path";
            }
            
            String sanitizedFilename = pluginPath.getFileName().toString();
            
            if (!Files.exists(pluginPath)) {
                return "Error: Plugin file does not exist: " + sanitizedFilename;
            }
            
            if (!Files.isRegularFile(pluginPath)) {
                return "Error: Not a regular file: " + sanitizedFilename;
            }
            
            Files.delete(pluginPath);
            logger.info("Plugin deleted successfully: {}", sanitizedFilename);
            return "Plugin deleted successfully: " + sanitizedFilename;
            
        } catch (IOException e) {
            logger.error("Error deleting plugin: {}", filename, e);
            return "Error deleting plugin: " + e.getMessage();
        }
    }
}
