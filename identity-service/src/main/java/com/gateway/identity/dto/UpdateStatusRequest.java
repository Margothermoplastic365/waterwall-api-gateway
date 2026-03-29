package com.gateway.identity.dto;

import com.gateway.identity.entity.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {

    @NotNull(message = "Status is required")
    private UserStatus status;

    private String reason;
}
