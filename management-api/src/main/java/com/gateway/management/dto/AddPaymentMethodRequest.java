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
public class AddPaymentMethodRequest {

    @NotBlank(message = "Payment method type is required")
    private String type;

    @NotBlank(message = "Provider is required")
    private String provider;

    @NotBlank(message = "Provider reference is required")
    private String providerRef;
}
