package com.gateway.notification.repository;

import com.gateway.notification.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, UUID> {

    Optional<NotificationPreferenceEntity> findByUserId(UUID userId);
}
