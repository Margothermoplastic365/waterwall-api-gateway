package com.gateway.analytics.repository;

import com.gateway.analytics.entity.AlertHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistoryEntity, Long> {

    Page<AlertHistoryEntity> findAllByOrderByTriggeredAtDesc(Pageable pageable);

    Page<AlertHistoryEntity> findByStatusOrderByTriggeredAtDesc(String status, Pageable pageable);
}
