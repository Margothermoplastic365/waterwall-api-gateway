package com.gateway.management.service;

import com.gateway.management.dto.CreateDocPageRequest;
import com.gateway.management.dto.DocPageResponse;
import com.gateway.management.entity.DocumentationPageEntity;
import com.gateway.management.repository.DocumentationPageRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentationService {

    private final DocumentationPageRepository repository;

    @Transactional
    public DocPageResponse createPage(UUID apiId, CreateDocPageRequest request) {
        DocumentationPageEntity entity = DocumentationPageEntity.builder()
                .apiId(apiId)
                .title(request.getTitle())
                .content(request.getContent())
                .docType(request.getDocType())
                .version(request.getVersion())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();
        entity = repository.save(entity);
        log.info("Created documentation page '{}' for API {}", entity.getTitle(), apiId);
        return toResponse(entity);
    }

    public List<DocPageResponse> listPages(UUID apiId, String version) {
        List<DocumentationPageEntity> pages;
        if (version != null && !version.isBlank()) {
            pages = repository.findByApiIdAndVersionOrderBySortOrderAsc(apiId, version);
        } else {
            pages = repository.findByApiIdOrderBySortOrderAsc(apiId);
        }
        return pages.stream().map(this::toResponse).toList();
    }

    public DocPageResponse getPage(UUID id) {
        DocumentationPageEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Documentation page not found: " + id));
        return toResponse(entity);
    }

    @Transactional
    public DocPageResponse updatePage(UUID id, CreateDocPageRequest request) {
        DocumentationPageEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Documentation page not found: " + id));

        entity.setTitle(request.getTitle());
        entity.setContent(request.getContent());
        entity.setDocType(request.getDocType());
        entity.setVersion(request.getVersion());
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        entity = repository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public void deletePage(UUID id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Documentation page not found: " + id);
        }
        repository.deleteById(id);
        log.info("Deleted documentation page {}", id);
    }

    @Transactional
    public void feedback(UUID id, String vote) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Documentation page not found: " + id);
        }
        if ("up".equalsIgnoreCase(vote)) {
            repository.incrementFeedbackUp(id);
        } else if ("down".equalsIgnoreCase(vote)) {
            repository.incrementFeedbackDown(id);
        } else {
            throw new IllegalArgumentException("Vote must be 'up' or 'down'");
        }
    }

    public List<DocPageResponse> search(String query) {
        return repository.fullTextSearch(query).stream()
                .map(this::toResponse)
                .toList();
    }

    private DocPageResponse toResponse(DocumentationPageEntity entity) {
        return DocPageResponse.builder()
                .id(entity.getId())
                .apiId(entity.getApiId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .docType(entity.getDocType())
                .version(entity.getVersion())
                .sortOrder(entity.getSortOrder())
                .feedbackUp(entity.getFeedbackUp())
                .feedbackDown(entity.getFeedbackDown())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
