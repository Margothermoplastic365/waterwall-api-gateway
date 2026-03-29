package com.gateway.management.repository;

import com.gateway.management.entity.MaintenanceWindowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindowEntity, UUID> {

    @Query("SELECT m FROM MaintenanceWindowEntity m WHERE m.endTime > :now ORDER BY m.startTime ASC")
    List<MaintenanceWindowEntity> findUpcoming(@Param("now") Instant now);

    List<MaintenanceWindowEntity> findAllByOrderByStartTimeDesc();
}
