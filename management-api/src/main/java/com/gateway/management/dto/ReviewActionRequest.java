package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewActionRequest {

    private boolean approved;
    private String rejectionReason;
}
