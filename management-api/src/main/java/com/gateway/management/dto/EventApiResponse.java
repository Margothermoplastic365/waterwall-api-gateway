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
public class EventApiResponse {

    private UUID id;
    private String name;
    private String protocol;
    private String connectionConfig;
    private String topics;
    private String schemaConfig;
    private Instant createdAt;
}
