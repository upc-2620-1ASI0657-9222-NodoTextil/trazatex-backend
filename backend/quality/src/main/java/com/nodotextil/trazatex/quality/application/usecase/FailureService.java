package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.DiscardFailureResult;
import com.nodotextil.trazatex.quality.application.dto.FailureInfoCommand;
import com.nodotextil.trazatex.quality.application.dto.FailureResult;
import com.nodotextil.trazatex.quality.application.port.in.ManageFailuresUseCase;
import com.nodotextil.trazatex.quality.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityEventPublisherPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityFailureRepositoryPort;
import com.nodotextil.trazatex.quality.domain.event.QualityStatusChanged;
import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.QualityFailure;
import com.nodotextil.trazatex.quality.domain.model.QualityStatusChange;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * US26: completar la información y enviar a reevaluación (sin llamar a Production: el lote sigue retenido). El descarte (US27) sí llama a
 * Production y vive aparte, en {@link FailureDiscardService}, que no es transaccional.
 */
@Service
class FailureService implements ManageFailuresUseCase {

    static final String FAILURE_UPDATED = "FAILURE_UPDATED";
    static final String FAILURE_REEVALUATION_STARTED = "FAILURE_REEVALUATION_STARTED";

    private final QualityFailureRepositoryPort failures;
    private final BatchProjectionRepositoryPort projections;
    private final QualityEventPublisherPort events;
    private final AuditLogPort audit;
    private final FailureDiscardService discards;
    private final Clock clock;

    FailureService(QualityFailureRepositoryPort failures, BatchProjectionRepositoryPort projections, QualityEventPublisherPort events, AuditLogPort audit,
                   FailureDiscardService discards, Clock clock) {
        this.failures = failures;
        this.projections = projections;
        this.events = events;
        this.audit = audit;
        this.discards = discards;
        this.clock = clock;
    }

    static NotFoundException notFound() {
        return new NotFoundException(ErrorCodes.FAILURE_NOT_FOUND, "La falla no existe.");
    }

    @Override
    @Transactional(readOnly = true)
    public FailureResult get(UUID failureId, Actor actor) {
        return failures.findById(failureId).filter(found -> found.companyId().equals(actor.companyId())).map(FailureResult::of)
                .orElseThrow(FailureService::notFound);
    }

    @Override
    @Transactional
    public FailureResult complete(UUID failureId, FailureInfoCommand command, Actor actor) {
        QualityFailure failure = failures.findByIdForUpdate(failureId).filter(found -> found.companyId().equals(actor.companyId()))
                .orElseThrow(FailureService::notFound);
        failure.complete(command.rootCause(), command.affectedQtyKg(), command.observations());
        failures.save(failure);
        events.publish(com.nodotextil.trazatex.quality.domain.event.QualityFailureUpdated.of(failure));
        audit.record(actor.userId(), failure.companyId(), FAILURE_UPDATED, "QualityFailure", failure.id().toString(),
                Map.of("batchId", failure.batchId().toString(), "affectedQtyKg", failure.affectedQtyKg().toPlainString()));
        return FailureResult.of(failure);
    }

    @Override
    @Transactional
    public FailureResult reevaluate(UUID failureId, Actor actor) {
        QualityFailure failure = failures.findByIdForUpdate(failureId).filter(found -> found.companyId().equals(actor.companyId()))
                .orElseThrow(FailureService::notFound);
        failure.startReevaluation();
        BatchProjection batch = projections.findByIdForUpdate(failure.batchId())
                .orElseThrow(() -> new IllegalStateException("El lote " + failure.batchId() + " no está en batch_projection"));
        QualityStatusChange change = batch.sendToReevaluation(clock.instant());
        projections.update(batch);
        events.publish(QualityStatusChanged.of(batch.batchId(), batch.companyId(), change, failure.controlId()));
        audit.record(actor.userId(), failure.companyId(), FAILURE_REEVALUATION_STARTED, "QualityFailure", failure.id().toString(),
                Map.of("batchId", failure.batchId().toString(), "qualityStatus", change.current().name()));
        return FailureResult.of(failure);
    }

    @Override
    public DiscardFailureResult discard(UUID failureId, Actor actor) {
        return discards.discard(failureId, actor);
    }
}
