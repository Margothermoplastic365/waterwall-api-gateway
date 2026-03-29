package com.gateway.management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalActionRequest {

    private String type;
    private UUID resourceId;
    private String reason;
}
