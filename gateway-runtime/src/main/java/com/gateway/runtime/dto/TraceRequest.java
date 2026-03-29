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
public class TraceRequest {

    private String path;
    private String method;
    private Map<String, String> headers;
    private String body;
}
