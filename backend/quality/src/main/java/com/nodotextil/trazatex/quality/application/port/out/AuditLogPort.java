package com.nodotextil.trazatex.quality.application.port.out;

import java.util.Map;
import java.util.UUID;

/** La fila de auditoría se escribe en la misma transacción que el cambio. {@code userId} nulo = acción del sistema. */
public interface AuditLogPort {

    void record(UUID userId, UUID companyId, String action, String entityType, String entityId, Map<String, Object> changes);
}
