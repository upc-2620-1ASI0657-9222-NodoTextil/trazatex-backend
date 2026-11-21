package com.nodotextil.trazatex.production.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface AuditLogPort {

    void record(UUID userId, UUID companyId, String action, String entityType, String entityId, Map<String, Object> changes);
}
