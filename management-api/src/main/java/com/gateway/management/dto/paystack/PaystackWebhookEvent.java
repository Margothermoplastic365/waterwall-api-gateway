package com.gateway.management.dto.paystack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaystackWebhookEvent {
    private String event;
    private Map<String, Object> data;
}
