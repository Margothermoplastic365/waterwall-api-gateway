package com.gateway.management.repository;

import com.gateway.management.entity.EventApiEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventApiRepository extends JpaRepository<EventApiEntity, UUID> {

    List<EventApiEntity> findByProtocol(String protocol);

    List<EventApiEntity> findByNameContainingIgnoreCase(String name);
}
