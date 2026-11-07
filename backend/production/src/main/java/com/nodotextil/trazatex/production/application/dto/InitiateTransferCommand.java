package com.nodotextil.trazatex.production.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Una transferencia es de lotes completos. {@code quantitiesKg} (opcional, {@code batchId → kg}) existe para que quien quiera
 * transferir solo una parte de un lote reciba un 400 que le pida dividirlo antes (US21 esc. 2), en vez de que el pedido se
 * ignore y se transfiera todo; un valor igual a la cantidad del lote pasa, uno distinto no.
 */
public record InitiateTransferCommand(UUID targetCompanyId, List<UUID> batchIds, Map<UUID, BigDecimal> quantitiesKg) {

    public InitiateTransferCommand(UUID targetCompanyId, List<UUID> batchIds) {
        this(targetCompanyId, batchIds, null);
    }
}
