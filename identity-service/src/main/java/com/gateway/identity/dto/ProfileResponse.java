package com.gateway.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {

    private String displayName;

    private String phone;

    private boolean phoneVerified;

    private String avatarUrl;

    private String bio;

    private String timezone;

    private String language;

    private String jobTitle;

    private String department;
}
