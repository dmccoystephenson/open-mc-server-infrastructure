package com.openmc.backupmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackupManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackupManagerApplication.class, args);
    }
}
