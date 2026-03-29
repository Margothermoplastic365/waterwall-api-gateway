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
public class PolicyResponse {

    private UUID id;
    private String name;
    private String type;
    private String config;
    private String description;
    private Integer version;
    private Instant createdAt;
}
