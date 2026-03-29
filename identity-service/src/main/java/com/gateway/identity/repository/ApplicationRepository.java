package com.gateway.identity.repository;

import com.gateway.identity.entity.ApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {

    List<ApplicationEntity> findByUserId(UUID userId);

    List<ApplicationEntity> findByUserIdAndStatusNot(UUID userId, String status);

    List<ApplicationEntity> findByOrganizationId(UUID orgId);
}
