package com.gateway.management.repository;

import com.gateway.management.entity.ConsentRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecordEntity, UUID> {

    List<ConsentRecordEntity> findByUserId(UUID userId);

    List<ConsentRecordEntity> findByUserIdAndGrantedTrue(UUID userId);
}
