package com.gateway.management.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewSubscriptionRequest {

    @Size(max = 500, message = "Reason must be 500 characters or less")
    private String reason;
}
