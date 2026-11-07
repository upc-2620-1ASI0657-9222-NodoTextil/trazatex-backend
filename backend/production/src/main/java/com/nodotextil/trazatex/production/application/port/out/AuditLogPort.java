package com.nodotextil.trazatex.production.application.port.out;

import java.util.Map;
import java.util.UUID;

/**
 * {@code audit_log} (RNF-11): toda operación sobre lotes, transformaciones y transferencias, en la misma transacción.
 * {@code userId} nulo = acción del sistema (p. ej. un {@code HOLD} del comando interno sin {@code requestedBy}).
 */
public interface AuditLogPort {

    void record(UUID userId, UUID companyId, String action, String entityType, String entityId, Map<String, Object> changes);
}
