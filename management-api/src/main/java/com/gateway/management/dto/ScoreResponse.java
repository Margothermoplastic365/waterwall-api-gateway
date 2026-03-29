package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreResponse {

    private UUID apiId;
    private int totalScore;
    private Map<String, Integer> breakdown;
    private List<String> recommendations;
}
