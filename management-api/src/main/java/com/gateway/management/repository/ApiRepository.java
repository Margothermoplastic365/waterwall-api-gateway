package com.gateway.management.repository;

import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.enums.ApiStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApiRepository extends JpaRepository<ApiEntity, UUID>, JpaSpecificationExecutor<ApiEntity> {

    Page<ApiEntity> findByStatus(ApiStatus status, Pageable pageable);

    List<ApiEntity> findByOrgId(UUID orgId);

    @Query("SELECT a FROM ApiEntity a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<ApiEntity> searchByNameOrDescription(@Param("keyword") String keyword, Pageable pageable);
}
