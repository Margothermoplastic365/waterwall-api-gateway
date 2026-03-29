package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdeCatalogEntry {

    private UUID id;
    private String name;
    private String version;
    private String status;
    private String protocol;
    private String category;
}
