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
public class DocPageResponse {

    private UUID id;
    private UUID apiId;
    private String title;
    private String content;
    private String docType;
    private String version;
    private Integer sortOrder;
    private Integer feedbackUp;
    private Integer feedbackDown;
    private Instant createdAt;
    private Instant updatedAt;
}
