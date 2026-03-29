package com.gateway.management.dto.paystack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaystackVerifyResponse {
    private boolean status;
    private String message;
    private PaystackVerifyData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaystackVerifyData {
        private String status;
        private String reference;
        private long amount;
        private String currency;
        private String channel;
        private String paid_at;
        private Map<String, Object> metadata;
        private PaystackAuthorization authorization;
        private PaystackCustomer customer;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaystackAuthorization {
        private String authorization_code;
        private String card_type;
        private String last4;
        private String bank;
        private String brand;
        private boolean reusable;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaystackCustomer {
        private String customer_code;
        private String email;
    }
}
