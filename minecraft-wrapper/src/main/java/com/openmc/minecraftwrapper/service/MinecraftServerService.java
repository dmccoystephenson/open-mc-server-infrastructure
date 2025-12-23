package com.openmc.minecraftwrapper.service;

import com.openmc.minecraftwrapper.model.ServerStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class MinecraftServerService {

    @Value("${minecraft.server.jar}")
    private String serverJar;

    @Value("${minecraft.server.directory}")
    private String serverDirectory;

    @Value("${minecraft.java.opts:-Xmx2G -Xms1G}")
    private String javaOpts;

    @Value("${minecraft.auto.start:false}")
    private boolean autoStart;

    private final AlertService alertService;
    private final ShutdownService shutdownService;

    private Process serverProcess;
    private Path inputFifo;
    private Thread fifoKeeperThread;
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);

    public MinecraftServerService(AlertService alertService, ShutdownService shutdownService) {
        this.alertService = alertService;
        this.shutdownService = shutdownService;
    }

    @PostConstruct
    public void startServer() {
        if (!autoStart) {
            log.info("Auto-start disabled, server will not start automatically");
            return;
        }
        
        try {
            log.info("Starting Minecraft server with wrapper...");
            log.info("Server JAR: {}", serverJar);
            log.info("Server Directory: {}", serverDirectory);
            log.info("Java Options: {}", javaOpts);

            File serverDir = new File(serverDirectory);
            if (!serverDir.exists() || !serverDir.isDirectory()) {
                throw new IllegalStateException("Server directory does not exist: " + serverDirectory);
            }

            // Create named pipe (FIFO) for passing commands to the server
            inputFifo = Path.of(serverDirectory, "server_input");
            if (Files.exists(inputFifo)) {
                Files.delete(inputFifo);
            }

            // Create FIFO using mkfifo command
            ProcessBuilder fifoBuilder = new ProcessBuilder("mkfifo", inputFifo.toString());
            Process fifoProcess = fifoBuilder.start();
            if (!fifoProcess.waitFor(5, TimeUnit.SECONDS) || fifoProcess.exitValue() != 0) {
                throw new IOException("Failed to create FIFO");
            }

            // Start FIFO keeper thread to keep the pipe open
            startFifoKeeper();

            // Build the Java command to start Minecraft server
            List<String> command = new ArrayList<>();
            command.add("java");
            // Split Java options
            for (String opt : javaOpts.split("\\s+")) {
                if (!opt.isEmpty()) {
                    command.add(opt);
                }
            }
            command.add("-jar");
            command.add(serverJar);
            command.add("nogui");

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(serverDir);
            processBuilder.redirectInput(inputFifo.toFile());
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

            serverProcess = processBuilder.start();
            log.info("Minecraft server started with PID: {}", serverProcess.pid());

            // Send alert that server has started
            alertService.sendServerStartAlert();

            // Monitor server process in a separate thread
            monitorServerProcess();

        } catch (Exception e) {
            log.error("Failed to start Minecraft server", e);
            throw new RuntimeException("Failed to start Minecraft server", e);
        }
    }

    private void startFifoKeeper() {
        fifoKeeperThread = new Thread(() -> {
            try (FileOutputStream fos = new FileOutputStream(inputFifo.toFile())) {
                // Keep the FIFO open by holding it open for writing
                // The thread will be interrupted when we want to stop
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(3600000); // Sleep for an hour at a time
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("FIFO keeper thread interrupted");
            } catch (IOException e) {
                log.error("Error in FIFO keeper thread", e);
            }
        });
        fifoKeeperThread.setDaemon(true);
        fifoKeeperThread.start();
    }

    private void monitorServerProcess() {
        Thread monitorThread = new Thread(() -> {
            try {
                int exitCode = serverProcess.waitFor();
                log.info("Minecraft server process exited with code: {}", exitCode);

                // Only send alerts if not in shutdown process
                if (!shutdownInProgress.get()) {
                    if (exitCode == 0) {
                        alertService.sendServerStopAlert();
                    } else {
                        alertService.sendServerCrashAlert(exitCode);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Server monitor thread interrupted");
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    @PreDestroy
    public void shutdown() {
        if (serverProcess != null && serverProcess.isAlive()) {
            shutdownInProgress.set(true);
            
            shutdownService.performGracefulShutdown(() -> {
                try {
                    // Send stop command via FIFO
                    sendCommand("stop");
                    
                    // Wait for server to shutdown gracefully
                    log.info("Waiting for server to shutdown gracefully...");
                    if (!serverProcess.waitFor(30, TimeUnit.SECONDS)) {
                        log.warn("Server did not shutdown in time, forcing termination");
                        serverProcess.destroyForcibly();
                    }
                    
                    log.info("Server shutdown gracefully");
                    alertService.sendServerStopAlert();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting for server shutdown");
                    serverProcess.destroyForcibly();
                } catch (IOException e) {
                    log.error("Failed to send stop command", e);
                    serverProcess.destroy();
                }
            });
        }

        // Cleanup FIFO keeper thread
        if (fifoKeeperThread != null && fifoKeeperThread.isAlive()) {
            fifoKeeperThread.interrupt();
        }

        // Cleanup FIFO
        if (inputFifo != null && Files.exists(inputFifo)) {
            try {
                Files.delete(inputFifo);
            } catch (IOException e) {
                log.warn("Failed to delete FIFO", e);
            }
        }
    }

    public void sendCommand(String command) throws IOException {
        if (serverProcess == null || !serverProcess.isAlive()) {
            throw new IllegalStateException("Server is not running");
        }

        try (FileWriter writer = new FileWriter(inputFifo.toFile(), true)) {
            writer.write(command + "\n");
            writer.flush();
            log.info("Command sent to server: {}", command);
        }
    }

    public ServerStatus getStatus() {
        return ServerStatus.builder()
                .running(serverProcess != null && serverProcess.isAlive())
                .pid(serverProcess != null && serverProcess.isAlive() ? serverProcess.pid() : null)
                .serverJar(serverJar)
                .serverDirectory(serverDirectory)
                .build();
    }
}
