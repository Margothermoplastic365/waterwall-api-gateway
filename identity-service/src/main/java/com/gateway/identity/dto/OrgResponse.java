package com.gateway.identity.dto;

import com.gateway.identity.entity.enums.OrgStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OrgResponse {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String domain;
    private String logoUrl;
    private OrgStatus status;
    private long memberCount;
    private Instant createdAt;
}
