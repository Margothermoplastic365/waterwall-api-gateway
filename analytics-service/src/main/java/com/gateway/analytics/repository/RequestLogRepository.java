package com.gateway.analytics.repository;

import com.gateway.analytics.entity.RequestLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLogEntity, Long> {
}
