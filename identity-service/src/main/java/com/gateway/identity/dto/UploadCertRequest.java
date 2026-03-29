package com.gateway.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UploadCertRequest {

    @NotBlank(message = "Certificate PEM content is required")
    private String certificatePem;
}
