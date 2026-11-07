package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.DiscardFailureResult;
import com.nodotextil.trazatex.quality.application.dto.FailureResult;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityFailureRepositoryPort;
import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.FailureStatus;
import com.nodotextil.trazatex.quality.domain.model.QualityFailure;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * US27, en tres fases y ninguna dentro de la transacción del llamador: <b>A</b> deja el descarte pendiente (la falla sigue {@code OPEN}), luego
 * {@code DISCARD} a Production y <b>B</b> aplica la respuesta ({@link FailureDiscardResolver}). No es {@code @Transactional} a propósito.
 */
@Component
class FailureDiscardService {

    private final QualityFailureRepositoryPort failures;
    private final BatchProjectionRepositoryPort projections;
    private final FailureDiscardResolver resolver;
    private final PendingCommandSettings settings;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    FailureDiscardService(QualityFailureRepositoryPort failures, BatchProjectionRepositoryPort projections, FailureDiscardResolver resolver,
                          PendingCommandSettings settings, Clock clock, PlatformTransactionManager transactionManager) {
        this.failures = failures;
        this.projections = projections;
        this.resolver = resolver;
        this.settings = settings;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private record Requested(QualityFailure failure, boolean sendCommand) {
    }

    DiscardFailureResult discard(UUID failureId, Actor actor) {
        Requested requested = requiresNew.execute(tx -> request(failureId, actor));
        if (!requested.sendCommand()) {
            return new DiscardFailureResult(FailureResult.of(requested.failure()), false);
        }
        return answer(resolver.resolve(requested.failure(), 0));
    }

    private Requested request(UUID failureId, Actor actor) {
        QualityFailure failure = failures.findByIdForUpdate(failureId).filter(found -> found.companyId().equals(actor.companyId()))
                .orElseThrow(FailureService::notFound);
        BatchProjection batch = projections.findById(failure.batchId())
                .orElseThrow(() -> new IllegalStateException("El lote " + failure.batchId() + " no está en batch_projection"));
        if (failure.status() == FailureStatus.OPEN && !failure.discardPending()) {
            batch.requireFailedToDiscard();
        }
        Instant now = clock.instant();
        boolean requested = failure.requestDiscard(actor.userId(), now);
        if (requested) {
            failures.saveDiscardRequest(failure, now.plus(settings.grace()));
        }
        return new Requested(failure, requested);
    }

    private DiscardFailureResult answer(FailureDiscardResolver.Resolution resolution) {
        FailureResult result = FailureResult.of(resolution.failure());
        return switch (resolution.kind()) {
            case CONFIRMED -> new DiscardFailureResult(result, true);
            case PENDING -> new DiscardFailureResult(result, false);
            case REJECTED -> {
                if (resolution.httpStatus() == 404) {
                    throw new NotFoundException(ErrorCodes.BATCH_NOT_FOUND, "El lote no existe.");
                }
                throw new ConflictException(resolution.code(), "No se pudo descartar el lote: " + resolution.message());
            }
            case DEFECT -> throw new IllegalStateException("Production rechazó DISCARD de la falla " + result.id() + ": " + resolution.httpStatus() + " "
                    + resolution.code());
            case ALREADY_RESOLVED -> new DiscardFailureResult(result, resolution.failure().status() == FailureStatus.CONFIRMED);
        };
    }
}
