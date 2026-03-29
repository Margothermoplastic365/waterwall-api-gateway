package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LintReportResponse {

    private UUID apiId;
    private String apiName;
    private int score;
    private List<LintViolation> violations;
    private Instant lintedAt;
}
