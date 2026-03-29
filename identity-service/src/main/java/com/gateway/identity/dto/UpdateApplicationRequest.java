package com.gateway.identity.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateApplicationRequest {

    private String name;

    private String description;

    private List<String> callbackUrls;
}
