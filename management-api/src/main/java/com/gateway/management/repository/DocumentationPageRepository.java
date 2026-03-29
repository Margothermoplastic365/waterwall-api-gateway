package com.gateway.management.repository;

import com.gateway.management.entity.DocumentationPageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentationPageRepository extends JpaRepository<DocumentationPageEntity, UUID> {

    List<DocumentationPageEntity> findByApiIdOrderBySortOrderAsc(UUID apiId);

    List<DocumentationPageEntity> findByApiIdAndVersionOrderBySortOrderAsc(UUID apiId, String version);

    @Query(value = "SELECT * FROM gateway.documentation_pages " +
            "WHERE search_vector @@ plainto_tsquery('english', :query) " +
            "ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC",
            nativeQuery = true)
    List<DocumentationPageEntity> fullTextSearch(@Param("query") String query);

    @Modifying
    @Query("UPDATE DocumentationPageEntity d SET d.feedbackUp = d.feedbackUp + 1 WHERE d.id = :id")
    void incrementFeedbackUp(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE DocumentationPageEntity d SET d.feedbackDown = d.feedbackDown + 1 WHERE d.id = :id")
    void incrementFeedbackDown(@Param("id") UUID id);
}
