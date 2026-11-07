package com.nodotextil.trazatex.production.domain.event;

import com.nodotextil.trazatex.production.domain.model.Transfer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Tópico {@code production.transfer}, clave transferId. Lo consumen traceability, quality, notification y analytics. */
public record TransferAccepted(UUID transferId, List<UUID> batchIds, UUID fromCompanyId, UUID toCompanyId, Instant acceptedAt)
        implements ProductionEvent {

    public static final String TYPE = "TransferAccepted";

    public static TransferAccepted of(Transfer transfer) {
        return new TransferAccepted(transfer.id(), transfer.batchIds(), transfer.originCompanyId(), transfer.targetCompanyId(),
                transfer.receptionTime());
    }

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return transferId.toString();
    }
}
