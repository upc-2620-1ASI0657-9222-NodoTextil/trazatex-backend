package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.Transfer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Una transferencia con un resumen de sus lotes: la empresa destino todavía no es dueña de ellos (no puede consultarlos
 * por id) y necesita saber qué le llega. Es lo mínimo para decidir: código, nombre, tipo de material y cantidad.
 */
public record TransferDetailResult(TransferResult transfer, List<BatchSummary> batches) {

    public record BatchSummary(UUID batchId, String traceabilityId, String name, String materialType, BigDecimal quantityKg) {
    }

    public static TransferDetailResult of(Transfer transfer, List<Batch> batches) {
        return new TransferDetailResult(TransferResult.from(transfer), batches.stream()
                .map(b -> new BatchSummary(b.id(), b.traceabilityId().value(), b.name(), b.materialType(), b.quantity().kg()))
                .toList());
    }
}
