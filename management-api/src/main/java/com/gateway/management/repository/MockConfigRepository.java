package com.gateway.management.repository;

import com.gateway.management.entity.MockConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MockConfigRepository extends JpaRepository<MockConfigEntity, UUID> {

    List<MockConfigEntity> findByApiId(UUID apiId);

    Optional<MockConfigEntity> findByApiIdAndPathAndMethod(UUID apiId, String path, String method);

    List<MockConfigEntity> findByApiIdAndMockEnabledTrue(UUID apiId);

    boolean existsByApiIdAndMockEnabledTrue(UUID apiId);
}
