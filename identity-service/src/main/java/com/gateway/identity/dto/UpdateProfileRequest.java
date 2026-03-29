package com.gateway.identity.dto;

import lombok.Data;

/**
 * Update profile request. All fields are optional — only non-null fields are applied.
 */
@Data
public class UpdateProfileRequest {

    private String displayName;

    private String phone;

    private String avatarUrl;

    private String bio;

    private String timezone;

    private String language;

    private String jobTitle;

    private String department;
}
