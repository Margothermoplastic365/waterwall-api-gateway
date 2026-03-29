package com.gateway.identity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_profiles", schema = "identity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_profile_user"))
    private UserEntity user;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "phone_verified")
    private Boolean phoneVerified;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    @Column(name = "timezone", length = 50)
    private String timezone;

    @Column(name = "language", length = 10)
    private String language;

    @Column(name = "job_title", length = 255)
    private String jobTitle;

    @Column(name = "department", length = 255)
    private String department;
}
