package com.gateway.identity.repository;

import com.gateway.identity.entity.MfaSecretEntity;
import com.gateway.identity.entity.enums.MfaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MfaSecretRepository extends JpaRepository<MfaSecretEntity, UUID> {

    Optional<MfaSecretEntity> findByUserIdAndType(UUID userId, MfaType type);

    List<MfaSecretEntity> findByUserIdAndEnabledTrue(UUID userId);
}
