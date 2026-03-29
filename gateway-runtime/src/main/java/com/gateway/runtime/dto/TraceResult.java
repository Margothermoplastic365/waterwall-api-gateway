package com.gateway.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceResult {

    private String traceId;
    private List<TraceEntry> entries;
    private int finalStatus;
    private long totalDurationMs;
}
