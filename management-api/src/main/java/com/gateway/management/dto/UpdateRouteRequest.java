package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRouteRequest {

    private String path;
    private String method;
    private String upstreamUrl;
    private List<String> authTypes;
    private Integer priority;
    private Boolean stripPrefix;
    private Boolean enabled;
}
