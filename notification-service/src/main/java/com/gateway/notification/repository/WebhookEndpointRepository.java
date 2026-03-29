package com.gateway.notification.repository;

import com.gateway.notification.entity.WebhookEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpointEntity, UUID> {

    List<WebhookEndpointEntity> findByUserIdAndActiveTrue(UUID userId);

    List<WebhookEndpointEntity> findByUserId(UUID userId);
}
