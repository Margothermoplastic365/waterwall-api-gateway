package com.gateway.identity.repository;

import com.gateway.identity.entity.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    Optional<UserProfileEntity> findByUserId(UUID userId);
}
