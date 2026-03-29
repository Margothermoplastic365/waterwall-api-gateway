package com.gateway.identity.repository;

import com.gateway.identity.entity.RevocationListEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RevocationListRepository extends JpaRepository<RevocationListEntity, Long> {

    List<RevocationListEntity> findByRevocationTypeAndExpiresAtAfter(String type, Instant now);

    boolean existsByRevocationTypeAndCredentialIdAndExpiresAtAfter(String type, String credentialId, Instant now);
}
