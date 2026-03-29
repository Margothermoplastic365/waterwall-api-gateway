package com.gateway.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_methods", schema = "gateway")
public class PaymentMethodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "consumer_id", nullable = false)
    private UUID consumerId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "provider", length = 100)
    private String provider;

    @Column(name = "provider_ref", length = 500)
    private String providerRef;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "paystack_authorization_code", length = 500)
    private String paystackAuthorizationCode;

    @Column(name = "paystack_customer_code", length = 255)
    private String paystackCustomerCode;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "card_brand", length = 50)
    private String cardBrand;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
