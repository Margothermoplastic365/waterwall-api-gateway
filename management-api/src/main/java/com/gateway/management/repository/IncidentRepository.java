package com.gateway.management.repository;

import com.gateway.management.entity.IncidentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<IncidentEntity, UUID> {

    List<IncidentEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<IncidentEntity> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    List<IncidentEntity> findAllByOrderByCreatedAtDesc();
}
