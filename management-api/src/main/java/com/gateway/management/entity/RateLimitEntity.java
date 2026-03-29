package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rate_limits", schema = "gateway",
        uniqueConstraints = @UniqueConstraint(name = "uq_ratelimit_key_window", columnNames = {"key", "window_start"}))
public class RateLimitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "\"key\"", nullable = false, length = 500)
    private String key;

    @Column(name = "window_start", nullable = false)
    private Long windowStart;

    @Column(name = "\"count\"")
    private int count;

    @Column(name = "limit_value", nullable = false)
    private int limitValue;
}
