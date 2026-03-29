package com.gateway.management.repository;

import com.gateway.management.entity.ComplianceReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComplianceReportRepository extends JpaRepository<ComplianceReportEntity, UUID> {

    List<ComplianceReportEntity> findByTypeOrderByGeneratedAtDesc(String type);

    List<ComplianceReportEntity> findAllByOrderByGeneratedAtDesc();
}
