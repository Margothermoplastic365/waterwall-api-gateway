package com.gateway.management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDocPageRequest {

    @NotBlank
    private String title;

    private String content;

    @NotBlank
    private String docType;

    private String version;

    private Integer sortOrder;
}
