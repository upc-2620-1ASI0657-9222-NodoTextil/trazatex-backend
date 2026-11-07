package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.Transfer;
import com.nodotextil.trazatex.production.domain.model.TransferStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code receptionTime} solo si fue aceptada; {@code resolvedAt} es el momento de la aceptación o el rechazo. */
public record TransferResult(
        UUID id,
        UUID originCompanyId,
        UUID targetCompanyId,
        TransferStatus status,
        List<UUID> batchIds,
        Instant departureTime,
        Instant receptionTime,
        Instant resolvedAt,
        String rejectionReason,
        UUID initiatedBy,
        UUID resolvedBy) {

    public static TransferResult from(Transfer t) {
        return new TransferResult(t.id(), t.originCompanyId(), t.targetCompanyId(), t.status(), t.batchIds(), t.departureTime(),
                t.receptionTime(), t.resolvedAt(), t.rejectionReason(), t.initiatedBy(), t.resolvedBy());
    }
}
