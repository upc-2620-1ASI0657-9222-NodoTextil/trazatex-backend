package com.nodotextil.trazatex.organizationaccess.application.port.out;

import java.util.Map;
import java.util.UUID;

/** {@code audit_log} (RNF-11): cambios de estado de empresas y usuarios y decisiones sobre solicitudes. */
public interface AuditLogPort {

    void record(UUID userId, UUID companyId, String action, String entityType, String entityId, Map<String, Object> changes);
}
