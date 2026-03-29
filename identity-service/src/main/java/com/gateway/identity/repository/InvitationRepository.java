package com.gateway.identity.repository;

import com.gateway.identity.entity.InvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    Optional<InvitationEntity> findByToken(String token);

    List<InvitationEntity> findByOrganizationId(UUID orgId);
}
