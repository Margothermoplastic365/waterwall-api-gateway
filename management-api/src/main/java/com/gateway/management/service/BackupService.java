package com.gateway.management.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stub backup service for triggering pg_dump backups and listing backup history.
 * Actual backup execution is handled by external scripts; this service
 * records backup metadata and provides a management interface.
 */
@Slf4j
@Service
public class BackupService {

    private final List<Map<String, Object>> backupHistory = new ArrayList<>();

    /**
     * Trigger a backup (stub — records intent, actual pg_dump is scripted externally).
     */
    public Map<String, Object> triggerBackup(String description) {
        String backupId = UUID.randomUUID().toString();
        log.info("Backup triggered: id={}, description={}", backupId, description);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("backupId", backupId);
        record.put("description", description);
        record.put("status", "TRIGGERED");
        record.put("triggeredAt", Instant.now().toString());
        record.put("note", "Actual pg_dump execution is handled by external backup scripts");

        backupHistory.add(record);
        return record;
    }

    /**
     * List all backup records.
     */
    public List<Map<String, Object>> listBackups() {
        return List.copyOf(backupHistory);
    }
}
