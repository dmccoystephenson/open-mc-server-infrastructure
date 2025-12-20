package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Plugin uploaded successfully: {}", filename);
            return "Plugin uploaded successfully: " + filename;
            
        } catch (IOException e) {
            logger.error("Error uploading plugin: {}", filename, e);
            return "Error uploading plugin: " + e.getMessage();
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
        
        // Sanitize filename to prevent directory traversal
        filename = new File(filename).getName();
        
        if (!filename.endsWith(".jar")) {
            return "Error: File must be a .jar file";
        }
        
        String pluginsDir = serverConfig.getPluginsDirectory();
        Path pluginPath = Paths.get(pluginsDir).resolve(filename);
        
        try {
            if (!Files.exists(pluginPath)) {
                return "Error: Plugin file does not exist: " + filename;
            }
            
            if (!Files.isRegularFile(pluginPath)) {
                return "Error: Not a regular file: " + filename;
            }
            
            // Verify the file is in the plugins directory (prevent directory traversal)
            if (!pluginPath.toRealPath().getParent().equals(Paths.get(pluginsDir).toRealPath())) {
                return "Error: Invalid file path";
            }
            
            Files.delete(pluginPath);
            logger.info("Plugin deleted successfully: {}", filename);
            return "Plugin deleted successfully: " + filename;
            
        } catch (IOException e) {
            logger.error("Error deleting plugin: {}", filename, e);
            return "Error deleting plugin: " + e.getMessage();
        }
    }
}
