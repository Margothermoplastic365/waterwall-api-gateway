package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportApiRequest {

    private String content;
    private String url;
    private String format; // AUTO, OPENAPI, SWAGGER, ASYNCAPI, GRAPHQL, PROTOBUF, WSDL, POSTMAN, OPENRPC, ODATA, HAR
    private String sensitivity; // LOW, MEDIUM, HIGH, CRITICAL
    private String category;
    private String contextPath;
}
