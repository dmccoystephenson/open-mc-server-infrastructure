package com.openmc.upgrademanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UpgradeManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UpgradeManagerApplication.class, args);
    }
}
