package com.openmc.agentmanager.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    private String title;
    private String message;
    private String level;
    private String source;
    private List<String> destinations;
}
