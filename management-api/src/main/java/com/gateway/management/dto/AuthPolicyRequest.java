package com.gateway.management.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class AuthPolicyRequest {

    @Pattern(regexp = "ANY|ALL", message = "authMode must be either ANY or ALL")
    private String authMode;

    private boolean allowAnonymous;

    private List<String> enabledAuthTypes;
}
