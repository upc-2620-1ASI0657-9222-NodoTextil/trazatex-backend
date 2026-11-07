package com.nodotextil.trazatex.production.domain.event;

import com.nodotextil.trazatex.production.domain.model.Transfer;

import java.util.List;
import java.util.UUID;

/** Tópico {@code production.transfer}, clave transferId. Lo consume notification (avisa a los administradores de la empresa destino). */
public record TransferRejected(UUID transferId, List<UUID> batchIds, UUID fromCompanyId, UUID toCompanyId) implements ProductionEvent {

    public static final String TYPE = "TransferRejected";

    public static TransferRejected of(Transfer transfer) {
        return new TransferRejected(transfer.id(), transfer.batchIds(), transfer.originCompanyId(), transfer.targetCompanyId());
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
