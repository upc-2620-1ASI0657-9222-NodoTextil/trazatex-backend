package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.ForbiddenException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Transfer {

    private final UUID id;
    private final UUID originCompanyId;
    private final UUID targetCompanyId;
    private TransferStatus status;
    private final List<UUID> batchIds;
    private final Instant departureTime;
    private Instant resolvedAt;
    private String rejectionReason;
    private final UUID initiatedBy;
    private UUID resolvedBy;

    private Transfer(UUID id, UUID originCompanyId, UUID targetCompanyId, TransferStatus status, List<UUID> batchIds,
                     Instant departureTime, Instant resolvedAt, String rejectionReason, UUID initiatedBy, UUID resolvedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.originCompanyId = Objects.requireNonNull(originCompanyId, "originCompanyId");
        this.targetCompanyId = Objects.requireNonNull(targetCompanyId, "targetCompanyId");
        this.status = status;
        this.batchIds = List.copyOf(batchIds);
        this.departureTime = departureTime;
        this.resolvedAt = resolvedAt;
        this.rejectionReason = rejectionReason;
        this.initiatedBy = initiatedBy;
        this.resolvedBy = resolvedBy;
    }

    public static Transfer initiate(UUID id, UUID originCompanyId, CompanyProjection target, List<Batch> batches,
                                    UUID initiatedBy, Instant now) {
        return initiate(id, originCompanyId, target, batches, Map.of(), initiatedBy, now);
    }

    public static Transfer initiate(UUID id, UUID originCompanyId, CompanyProjection target, List<Batch> batches,
                                    Map<UUID, Quantity> requestedQuantities, UUID initiatedBy, Instant now) {
        if (batches == null || batches.isEmpty()) {
            throw new ValidationException(ErrorCodes.NO_BATCHES_SELECTED, "Seleccione al menos un lote para transferir.", null);
        }
        if (originCompanyId.equals(target.companyId())) {
            throw new ValidationException(ErrorCodes.SAME_COMPANY_TRANSFER,
                    "La empresa destinataria debe ser distinta de la de origen.", null);
        }
        if (!target.isActive()) {
            throw new ConflictException(ErrorCodes.TARGET_COMPANY_INACTIVE,
                    "La empresa destinataria está inactiva y no puede recibir transferencias.");
        }
        Set<UUID> seen = new HashSet<>();
        for (Batch batch : batches) {
            if (!seen.add(batch.id())) {
                throw new ValidationException(ErrorCodes.DUPLICATE_BATCH,
                        "El lote " + batch.traceabilityId() + " está repetido en la selección.", null);
            }
            if (!batch.companyId().equals(originCompanyId)) {
                throw new ConflictException(ErrorCodes.BATCH_COMPANY_MISMATCH,
                        "El lote " + batch.traceabilityId() + " no pertenece a la empresa de origen.");
            }
            Quantity requested = requestedQuantities.get(batch.id());
            if (requested != null && !requested.equals(batch.quantity())) {
                throw new ValidationException(ErrorCodes.PARTIAL_TRANSFER_NOT_ALLOWED,
                        "Solo se transfieren lotes completos: divida el lote antes", List.of(batch.traceabilityId().value()));
            }
            batch.ensureEnabled();
        }
        batches.forEach(Batch::startTransfer);
        return new Transfer(id, originCompanyId, target.companyId(), TransferStatus.PENDING,
                batches.stream().map(Batch::id).toList(), now, null, null, initiatedBy, null);
    }

    public static Transfer restore(UUID id, UUID originCompanyId, UUID targetCompanyId, TransferStatus status,
                                   List<UUID> batchIds, Instant departureTime, Instant resolvedAt,
                                   String rejectionReason, UUID initiatedBy, UUID resolvedBy) {
        return new Transfer(id, originCompanyId, targetCompanyId, status, batchIds, departureTime, resolvedAt,
                rejectionReason, initiatedBy, resolvedBy);
    }

    public void accept(UUID actingCompanyId, List<Batch> batches, UUID resolvedBy, Instant now) {
        ensureResolvable(actingCompanyId, batches);
        batches.forEach(batch -> batch.acceptTransfer(targetCompanyId));
        this.status = TransferStatus.ACCEPTED;
        this.resolvedAt = now;
        this.resolvedBy = resolvedBy;
    }

    public void reject(UUID actingCompanyId, List<Batch> batches, String reason, UUID resolvedBy, Instant now) {
        ensureResolvable(actingCompanyId, batches);
        if (reason == null || reason.isBlank()) {
            throw new ValidationException(ErrorCodes.REJECTION_REASON_REQUIRED, "Indique el motivo del rechazo.", null);
        }
        batches.forEach(Batch::rejectTransfer);
        this.status = TransferStatus.REJECTED;
        this.rejectionReason = reason.trim();
        this.resolvedAt = now;
        this.resolvedBy = resolvedBy;
    }

    private void ensureResolvable(UUID actingCompanyId, List<Batch> batches) {
        if (!targetCompanyId.equals(actingCompanyId)) {
            throw new ForbiddenException(ErrorCodes.NOT_TRANSFER_RECIPIENT,
                    "Solo la empresa destinataria puede resolver la transferencia.");
        }
        if (status != TransferStatus.PENDING) {
            throw new ConflictException(ErrorCodes.TRANSFER_NOT_PENDING, "La transferencia ya fue resuelta.");
        }
        Set<UUID> given = new HashSet<>();
        batches.forEach(batch -> given.add(batch.id()));
        if (!given.equals(new HashSet<>(batchIds)) || batches.size() != batchIds.size()) {
            throw new ConflictException(ErrorCodes.BATCHES_DO_NOT_MATCH, "Los lotes dados no son los de esta transferencia.");
        }
        batches.forEach(batch -> batch.ensurePhase(BatchPhase.IN_TRANSFER));
    }

    public UUID id() {
        return id;
    }

    public UUID originCompanyId() {
        return originCompanyId;
    }

    public UUID targetCompanyId() {
        return targetCompanyId;
    }

    public TransferStatus status() {
        return status;
    }

    public List<UUID> batchIds() {
        return batchIds;
    }

    public Instant departureTime() {
        return departureTime;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    public Instant receptionTime() {
        return status == TransferStatus.ACCEPTED ? resolvedAt : null;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public UUID initiatedBy() {
        return initiatedBy;
    }

    public UUID resolvedBy() {
        return resolvedBy;
    }
}
