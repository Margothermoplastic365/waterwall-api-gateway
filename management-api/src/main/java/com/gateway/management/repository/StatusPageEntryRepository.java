package com.gateway.management.repository;

import com.gateway.management.entity.StatusPageEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StatusPageEntryRepository extends JpaRepository<StatusPageEntryEntity, UUID> {

    Optional<StatusPageEntryEntity> findByServiceName(String serviceName);
}
