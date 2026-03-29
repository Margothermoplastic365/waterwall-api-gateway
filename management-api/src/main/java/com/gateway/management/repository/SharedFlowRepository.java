package com.gateway.management.repository;

import com.gateway.management.entity.SharedFlowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SharedFlowRepository extends JpaRepository<SharedFlowEntity, UUID> {
}
