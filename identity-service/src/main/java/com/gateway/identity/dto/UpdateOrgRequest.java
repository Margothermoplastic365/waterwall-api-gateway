package com.gateway.identity.dto;

import lombok.Data;

@Data
public class UpdateOrgRequest {

    private String name;
    private String description;
    private String domain;
    private String logoUrl;
}
