package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdeLintRequest {

    private String specContent;
    private String format;  // openapi3, swagger2, asyncapi
}
