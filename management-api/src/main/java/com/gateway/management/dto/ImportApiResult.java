package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportApiResult {

    private String name;
    private String version;
    private String description;
    private String protocolType; // REST, SOAP, GRAPHQL, GRPC, WEBSOCKET, SSE, KAFKA, RABBITMQ, MQTT, JSON_RPC, ODATA
    private String detectedFormat; // OPENAPI_3, SWAGGER_2, ASYNCAPI, GRAPHQL_SDL, PROTOBUF, WSDL, POSTMAN, OPENRPC, ODATA_EDMX, HAR
    private String rawSpec;

    @Builder.Default
    private List<ImportedRoute> routes = new ArrayList<>();

    @Builder.Default
    private List<String> authSchemes = new ArrayList<>();

    @Builder.Default
    private List<String> servers = new ArrayList<>();

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    // For async/event APIs
    @Builder.Default
    private List<ImportedChannel> channels = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportedRoute {
        private String path;
        private String method;
        private String description;
        private List<String> authTypes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportedChannel {
        private String name;
        private String protocol;
        private String description;
        private String publishSchema;
        private String subscribeSchema;
    }
}
