package com.gateway.notification.repository;

import com.gateway.notification.entity.WebhookDeliveryLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLogEntity, Long> {

    Page<WebhookDeliveryLogEntity> findByWebhookEndpointIdOrderByDeliveredAtDesc(UUID webhookEndpointId, Pageable pageable);
}
