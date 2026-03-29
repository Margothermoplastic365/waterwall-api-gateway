package com.gateway.analytics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuditRetentionService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${gateway.analytics.audit.retention-days:90}")
    private int retentionDays;

    public AuditRetentionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeOldAuditEvents() {
        log.info("Audit retention: starting purge of events older than {} days", retentionDays);

        int deleted = jdbcTemplate.update(
                "DELETE FROM analytics.audit_events_all WHERE created_at < NOW() - INTERVAL '" + retentionDays + " days'"
        );

        log.info("Audit retention: deleted {} events older than {} days", deleted, retentionDays);
    }
}
