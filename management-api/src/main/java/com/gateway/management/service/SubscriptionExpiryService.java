package com.gateway.management.service;

import com.gateway.common.events.EventPublisher;
import com.gateway.common.events.RabbitMQExchanges;
import com.gateway.management.entity.SubscriptionEntity;
import com.gateway.management.entity.enums.SubStatus;
import com.gateway.management.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionExpiryService {

    private final SubscriptionRepository subscriptionRepository;
    private final EventPublisher eventPublisher;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void checkExpiredSubscriptions() {
        List<SubStatus> activeStatuses = List.of(SubStatus.APPROVED, SubStatus.ACTIVE);
        List<SubscriptionEntity> expired = subscriptionRepository
                .findByExpiresAtBeforeAndStatusIn(Instant.now(), activeStatuses);

        if (expired.isEmpty()) {
            log.debug("No expired subscriptions found");
            return;
        }

        for (SubscriptionEntity sub : expired) {
            sub.setStatus(SubStatus.EXPIRED);
            sub.setReason("Subscription expired");
            subscriptionRepository.save(sub);

            BillingSchedulerService.BillingEvent event = BillingSchedulerService.BillingEvent.builder()
                    .eventType("subscription.expired")
                    .actorId("expiry-scheduler")
                    .consumerId(sub.getApplicationId().toString())
                    .build();
            eventPublisher.publish(RabbitMQExchanges.PLATFORM_EVENTS, "subscription.expired", event);

            log.info("Subscription {} expired (was due {})", sub.getId(), sub.getExpiresAt());
        }

        log.info("Expired {} subscriptions", expired.size());
    }
}
