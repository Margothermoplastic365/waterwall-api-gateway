package com.gateway.management.service;

import com.gateway.management.dto.ConnectorResponse;
import com.gateway.management.dto.CreateConnectorRequest;
import com.gateway.management.entity.ConnectorEntity;
import com.gateway.management.repository.ConnectorRepository;
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
public class ConnectorService {

    private final ConnectorRepository repository;

    @Transactional
    public ConnectorResponse create(CreateConnectorRequest request) {
        ConnectorEntity entity = ConnectorEntity.builder()
                .name(request.getName())
                .type(request.getType())
                .config(request.getConfig())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();
        entity = repository.save(entity);
        log.info("Created connector '{}' of type {}", entity.getName(), entity.getType());
        return toResponse(entity);
    }

    public List<ConnectorResponse> listAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public ConnectorResponse get(UUID id) {
        ConnectorEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Connector not found: " + id));
        return toResponse(entity);
    }

    @Transactional
    public ConnectorResponse update(UUID id, CreateConnectorRequest request) {
        ConnectorEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Connector not found: " + id));
        entity.setName(request.getName());
        entity.setType(request.getType());
        entity.setConfig(request.getConfig());
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
        entity = repository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Connector not found: " + id);
        }
        repository.deleteById(id);
        log.info("Deleted connector {}", id);
    }

    public ConnectorResponse testConnection(UUID id) {
        ConnectorEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Connector not found: " + id));
        log.info("Testing connection for connector '{}' (type={})", entity.getName(), entity.getType());
        // In a production implementation, this would attempt to connect to the external service.
        // For now, we simply return the connector details to confirm it exists.
        return toResponse(entity);
    }

    private ConnectorResponse toResponse(ConnectorEntity entity) {
        return ConnectorResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .config(entity.getConfig())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
