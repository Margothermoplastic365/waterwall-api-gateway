package com.gateway.identity.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ApplicationResponse {

    private UUID id;
    private String name;
    private String description;
    private List<String> callbackUrls;
    private String status;
    private UUID orgId;
    private Instant createdAt;
    private Instant updatedAt;
}
