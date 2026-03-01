package com.openmc.agentmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgentManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentManagerApplication.class, args);
    }
}
