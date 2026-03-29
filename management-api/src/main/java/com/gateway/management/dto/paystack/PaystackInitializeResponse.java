package com.gateway.management.dto.paystack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaystackInitializeResponse {
    private boolean status;
    private String message;
    private PaystackInitData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaystackInitData {
        private String authorization_url;
        private String access_code;
        private String reference;
    }
}
