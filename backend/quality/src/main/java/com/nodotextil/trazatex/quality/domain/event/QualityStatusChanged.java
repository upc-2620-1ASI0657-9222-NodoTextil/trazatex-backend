package com.nodotextil.trazatex.quality.domain.event;

import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import com.nodotextil.trazatex.quality.domain.model.QualityStatusChange;

import java.util.UUID;

/**
 * Tópico {@code quality.control}, clave batchId. Lo consumen production, traceability y analytics. {@code controlId} es nulo cuando el
 * cambio no viene de un control (un lote enviado a reevaluación, o retenido por una posible falla derivada).
 */
public record QualityStatusChanged(UUID batchId, UUID companyId, QualityStatus previousStatus, QualityStatus newStatus, UUID controlId)
        implements QualityEvent {

    public static final String TYPE = "QualityStatusChanged";

    public static QualityStatusChanged of(UUID batchId, UUID companyId, QualityStatusChange change, UUID controlId) {
        return new QualityStatusChanged(batchId, companyId, change.previous(), change.current(), controlId);
    }

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return batchId.toString();
    }
}
