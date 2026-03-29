package com.gateway.identity.dto;

import lombok.Data;

@Data
public class CreateApiKeyRequest {

    private String name;
    private String environmentSlug = "dev";
}
