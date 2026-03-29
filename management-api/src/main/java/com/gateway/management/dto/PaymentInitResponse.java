package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentInitResponse {
    private String authorizationUrl;
    private String reference;
    private String accessCode;
}
