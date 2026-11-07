package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.application.port.out.AuditLogPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.Types;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Component
class JdbcAuditLogAdapter implements AuditLogPort {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JdbcAuditLogAdapter(JdbcClient jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void record(UUID userId, UUID companyId, String action, String entityType, String entityId, Map<String, Object> changes) {
        jdbc.sql("""
                        INSERT INTO audit_log (id, user_id, company_id, action, entity_type, entity_id, changes, occurred_at)
                        VALUES (:id, :userId, :companyId, :action, :entityType, :entityId, cast(:changes AS jsonb), :occurredAt)
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId, Types.OTHER)       // nulo = acción del sistema
                .param("companyId", companyId, Types.OTHER)
                .param("action", action)
                .param("entityType", entityType)
                .param("entityId", entityId)
                .param("changes", changes == null ? null : objectMapper.writeValueAsString(changes))
                .param("occurredAt", Timestamps.toDb(clock.instant()))
                .update();
    }
}
