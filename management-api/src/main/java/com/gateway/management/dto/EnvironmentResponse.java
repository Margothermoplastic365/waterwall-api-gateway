package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentResponse {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String config;
    private int apiCount;
    private Instant createdAt;
}
