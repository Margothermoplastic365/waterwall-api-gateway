package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeprecateVersionRequest {

    private String message;
    private UUID successorVersionId;
}
