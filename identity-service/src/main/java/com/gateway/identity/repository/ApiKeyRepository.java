package com.gateway.identity.repository;

import com.gateway.identity.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {

    Optional<ApiKeyEntity> findByKeyHash(String keyHash);

    Optional<ApiKeyEntity> findByKeyPrefix(String keyPrefix);

    List<ApiKeyEntity> findByApplicationId(UUID applicationId);
}
