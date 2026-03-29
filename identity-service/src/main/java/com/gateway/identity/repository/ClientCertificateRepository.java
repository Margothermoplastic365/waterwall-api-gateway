package com.gateway.identity.repository;

import com.gateway.identity.entity.ClientCertificateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientCertificateRepository extends JpaRepository<ClientCertificateEntity, UUID> {

    Optional<ClientCertificateEntity> findByFingerprint(String fingerprint);

    Optional<ClientCertificateEntity> findBySubjectCnAndStatus(String cn, String status);

    List<ClientCertificateEntity> findByApplicationId(UUID appId);
}
