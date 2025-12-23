package com.openmc.minecraftwrapper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerStatus {
    private boolean running;
    private Long pid;
    private String serverJar;
    private String serverDirectory;
}
