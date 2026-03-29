package com.gateway.management.service;

import com.gateway.management.dto.ApiHubEntry;
import com.gateway.management.entity.ApiEntity;
import com.gateway.management.entity.ApiSpecEntity;
import com.gateway.management.entity.ApiTemplateEntity;
import com.gateway.management.entity.SubscriptionEntity;
import com.gateway.management.entity.enums.SubStatus;
import com.gateway.management.repository.ApiRepository;
import com.gateway.management.repository.ApiSpecRepository;
import com.gateway.management.repository.ApiTemplateRepository;
import com.gateway.management.repository.SubscriptionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiHubService {

    private static final Duration STALE_THRESHOLD = Duration.ofDays(90);

    private final ApiRepository apiRepository;
    private final ApiSpecRepository apiSpecRepository;
    private final ApiTemplateRepository apiTemplateRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public List<ApiHubEntry> getApiHub() {
        List<ApiEntity> apis = apiRepository.findAll();

        return apis.stream().map(api -> {
            List<SubscriptionEntity> subscriptions = subscriptionRepository.findByApiId(api.getId());
            int subscriberCount = (int) subscriptions.stream()
                    .filter(s -> s.getStatus() == SubStatus.ACTIVE)
                    .count();

            boolean isStale = api.getUpdatedAt() != null
                    && Duration.between(api.getUpdatedAt(), Instant.now()).compareTo(STALE_THRESHOLD) > 0;

            boolean isOrphan = subscriberCount == 0
                    && !"PUBLISHED".equals(api.getStatus().name());

            return ApiHubEntry.builder()
                    .apiId(api.getId())
                    .name(api.getName())
                    .version(api.getVersion())
                    .status(api.getStatus().name())
                    .subscriberCount(subscriberCount)
                    .dependencyCount(0) // dependencies tracking not yet implemented
                    .isStale(isStale)
                    .isOrphan(isOrphan)
                    .lastUpdated(api.getUpdatedAt())
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiTemplateEntity> listTemplates() {
        return apiTemplateRepository.findAll();
    }

    @Transactional
    public ApiSpecEntity createFromTemplate(UUID templateId, String apiName) {
        ApiTemplateEntity template = apiTemplateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + templateId));

        // Replace placeholders in template content
        String specContent = template.getTemplateContent();
        if (specContent != null) {
            specContent = specContent.replace("{{apiName}}", apiName);
            specContent = specContent.replace("{{resourcePlural}}", apiName.toLowerCase().replaceAll("\\s+", "-") + "s");
            specContent = specContent.replace("{{serviceName}}", apiName.toLowerCase().replaceAll("\\s+", ""));
        }

        // Create the API entity
        ApiEntity api = ApiEntity.builder()
                .name(apiName)
                .version("1.0.0")
                .description("Created from template: " + template.getName())
                .status(com.gateway.management.entity.enums.ApiStatus.CREATED)
                .protocolType(mapTemplateTypeToProtocol(template.getTemplateType()))
                .build();
        api = apiRepository.save(api);

        // Create the spec
        ApiSpecEntity spec = ApiSpecEntity.builder()
                .apiId(api.getId())
                .specContent(specContent)
                .specFormat("OPENAPI_3")
                .build();
        spec = apiSpecRepository.save(spec);

        log.info("Created API from template: apiId={}, templateId={}, name={}",
                api.getId(), templateId, apiName);

        return spec;
    }

    private String mapTemplateTypeToProtocol(String templateType) {
        if (templateType == null) return "REST";
        return switch (templateType) {
            case "GRAPHQL" -> "GRAPHQL";
            case "GRPC" -> "GRPC";
            case "EVENT_DRIVEN" -> "EVENT";
            default -> "REST";
        };
    }
}
