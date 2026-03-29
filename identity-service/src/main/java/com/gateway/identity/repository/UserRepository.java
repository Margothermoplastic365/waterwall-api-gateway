package com.gateway.identity.repository;

import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<UserEntity> findByEmailVerifyToken(String token);

    Page<UserEntity> findByStatus(UserStatus status, Pageable pageable);

    @Query("SELECT u FROM UserEntity u LEFT JOIN UserProfileEntity p ON p.user = u " +
            "WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(p.displayName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<UserEntity> searchByEmailOrDisplayName(@Param("keyword") String keyword, Pageable pageable);
}
