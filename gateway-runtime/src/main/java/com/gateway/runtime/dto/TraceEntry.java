package com.gateway.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceEntry {

    private String filterName;
    private int order;
    private String decision;
    private long durationMs;
    private Map<String, Object> details;
}
