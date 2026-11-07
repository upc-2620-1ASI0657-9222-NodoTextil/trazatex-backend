package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.InitiateTransferCommand;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.TransferDirection;
import com.nodotextil.trazatex.production.application.dto.TransferDetailResult;
import com.nodotextil.trazatex.production.application.dto.TransferResult;
import com.nodotextil.trazatex.production.application.port.in.ManageTransfersUseCase;
import com.nodotextil.trazatex.production.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.ProductionEventPublisherPort;
import com.nodotextil.trazatex.production.application.port.out.TransferRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.TransferSearchCriteria;
import com.nodotextil.trazatex.production.domain.event.TransferAccepted;
import com.nodotextil.trazatex.production.domain.event.TransferInitiated;
import com.nodotextil.trazatex.production.domain.event.TransferRejected;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.CompanyProjection;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.Transfer;
import com.nodotextil.trazatex.production.domain.model.TransferStatus;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
class TransferService implements ManageTransfersUseCase {

    private final TransferRepositoryPort transfers;
    private final BatchRepositoryPort batches;
    private final BatchChanges batchChanges;
    private final BatchLocks locks;
    private final CompanyGuard companyGuard;
    private final AuditLogPort audit;
    private final Clock clock;

    TransferService(TransferRepositoryPort transfers, BatchRepositoryPort batches, BatchChanges batchChanges, BatchLocks locks,
                    CompanyGuard companyGuard, AuditLogPort audit, Clock clock) {
        this.transfers = transfers;
        this.batches = batches;
        this.batchChanges = batchChanges;
        this.locks = locks;
        this.companyGuard = companyGuard;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public TransferResult initiate(InitiateTransferCommand command, Actor actor) {
        companyGuard.requireActive(actor.companyId());
        if (command.targetCompanyId() == null) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Faltan datos obligatorios: targetCompanyId.", List.of("targetCompanyId"));
        }
        List<UUID> ids = BatchLocks.requireDistinct(command.batchIds());
        Map<UUID, Quantity> requested = requestedQuantities(command, ids);
        CompanyProjection target = companyGuard.find(command.targetCompanyId()).orElseThrow(
                () -> new NotFoundException(ErrorCodes.TARGET_COMPANY_NOT_FOUND, "La empresa destinataria no existe."));
        List<Batch> lots = locks.lockOwned(ids, actor.companyId());
        Transfer transfer = Transfer.initiate(UUID.randomUUID(), actor.companyId(), target, lots, requested, actor.userId(), clock.instant());
        batchChanges.persistAndPublish(lots, PhaseChangeCause.of(PhaseChangeCause.TRANSFER_INITIATED, transfer.id()), TransferInitiated.of(transfer));
        transfers.save(transfer);
        audit.record(actor.userId(), actor.companyId(), "TRANSFER_INITIATED", "Transfer", transfer.id().toString(),
                BatchService.changes("targetCompanyId", target.companyId().toString(),
                        "batches", lots.stream().map(b -> b.traceabilityId().value()).toList()));
        return TransferResult.from(transfer);
    }

    @Override
    @Transactional(readOnly = true)
    public TransferDetailResult get(UUID transferId, Actor actor) {
        Transfer transfer = transfers.findById(transferId).filter(t -> involves(t, actor)).orElseThrow(TransferService::notFound);
        List<Batch> found = batches.findAllById(transfer.batchIds());
        List<Batch> ordered = transfer.batchIds().stream()
                .map(id -> found.stream().filter(b -> b.id().equals(id)).findFirst()).flatMap(java.util.Optional::stream).toList();
        return TransferDetailResult.of(transfer, ordered);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<TransferResult> search(TransferDirection direction, TransferStatus status, Integer page,
                                             Integer size, Actor actor) {
        return PageResult.of(transfers.search(new TransferSearchCriteria(actor.companyId(),
                TransferSearchCriteria.Direction.valueOf((direction == null ? TransferDirection.ALL : direction).name()), status, Paging.page(page), Paging.size(size)))
                .map(TransferResult::from));
    }

    /** {@code quantitiesKg}: solo de lotes de la selección y cantidades válidas (400 antes de bloquear nada). */
    private static Map<UUID, Quantity> requestedQuantities(InitiateTransferCommand command, List<UUID> batchIds) {
        Map<UUID, Quantity> requested = new HashMap<>();
        if (command.quantitiesKg() == null) {
            return requested;
        }
        for (Map.Entry<UUID, BigDecimal> entry : command.quantitiesKg().entrySet()) {
            if (entry.getKey() == null || !batchIds.contains(entry.getKey())) {
                throw new ValidationException(ErrorCodes.INVALID_QUANTITIES,
                        "quantitiesKg trae un lote que no está en batchIds: " + entry.getKey() + ".", List.of("quantitiesKg"));
            }
            requested.put(entry.getKey(), Quantity.positive(entry.getValue()));
        }
        return requested;
    }

    /** Aceptar incorpora material a la empresa: se bloquea si está inactiva (RF-003). */
    @Override
    public TransferResult accept(UUID transferId, Actor actor) {
        companyGuard.requireActive(actor.companyId());
        Transfer transfer = transfers.findByIdForUpdate(transferId).filter(t -> involves(t, actor)).orElseThrow(TransferService::notFound);
        List<Batch> lots = locks.lock(transfer.batchIds());
        transfer.accept(actor.companyId(), lots, actor.userId(), clock.instant());
        batchChanges.persistAndPublish(lots, PhaseChangeCause.of(PhaseChangeCause.TRANSFER_ACCEPTED, transfer.id()), TransferAccepted.of(transfer));
        transfers.save(transfer);
        audit.record(actor.userId(), actor.companyId(), "TRANSFER_ACCEPTED", "Transfer", transfer.id().toString(),
                BatchService.changes("originCompanyId", transfer.originCompanyId().toString(),
                        "batches", lots.stream().map(b -> b.traceabilityId().value()).toList()));
        return TransferResult.from(transfer);
    }

    /** Rechazar devuelve los lotes al origen: se permite aunque la empresa esté inactiva (RF-003, sin lotes atascados). */
    @Override
    public TransferResult reject(UUID transferId, String reason, Actor actor) {
        Transfer transfer = transfers.findByIdForUpdate(transferId).filter(t -> involves(t, actor)).orElseThrow(TransferService::notFound);
        List<Batch> lots = locks.lock(transfer.batchIds());
        transfer.reject(actor.companyId(), lots, reason, actor.userId(), clock.instant());
        batchChanges.persistAndPublish(lots, PhaseChangeCause.of(PhaseChangeCause.TRANSFER_REJECTED, transfer.id()), TransferRejected.of(transfer));
        transfers.save(transfer);
        audit.record(actor.userId(), actor.companyId(), "TRANSFER_REJECTED", "Transfer", transfer.id().toString(),
                BatchService.changes("originCompanyId", transfer.originCompanyId().toString(), "reason", transfer.rejectionReason(),
                        "batches", lots.stream().map(b -> b.traceabilityId().value()).toList()));
        return TransferResult.from(transfer);
    }

    /** Solo las dos empresas de la transferencia la ven; para cualquier otra "no existe". */
    private static boolean involves(Transfer transfer, Actor actor) {
        return actor.companyId().equals(transfer.originCompanyId()) || actor.companyId().equals(transfer.targetCompanyId());
    }

    private static NotFoundException notFound() {
        return new NotFoundException(ErrorCodes.TRANSFER_NOT_FOUND, "La transferencia no existe.");
    }
}
