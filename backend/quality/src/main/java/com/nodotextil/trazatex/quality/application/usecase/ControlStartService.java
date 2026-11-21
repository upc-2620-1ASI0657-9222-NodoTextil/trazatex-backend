package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.ControlResult;
import com.nodotextil.trazatex.quality.application.dto.StartControlResult;
import com.nodotextil.trazatex.quality.application.port.in.GetQualityControlUseCase;
import com.nodotextil.trazatex.quality.application.port.in.StartQualityControlUseCase;
import com.nodotextil.trazatex.quality.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.ControlStatus;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
class ControlStartService implements StartQualityControlUseCase, GetQualityControlUseCase {

    static final String CONTROL_STARTED = "CONTROL_STARTED";

    private final BatchProjectionRepositoryPort batches;
    private final QualityControlRepositoryPort controls;
    private final AuditLogPort audit;
    private final ControlStartResolver resolver;
    private final PendingCommandSettings settings;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    ControlStartService(BatchProjectionRepositoryPort batches, QualityControlRepositoryPort controls, AuditLogPort audit, ControlStartResolver resolver,
                        PendingCommandSettings settings, Clock clock, PlatformTransactionManager transactionManager) {
        this.batches = batches;
        this.controls = controls;
        this.audit = audit;
        this.resolver = resolver;
        this.settings = settings;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public StartControlResult start(UUID batchId, Actor actor, String internalObservations) {
        QualityControl control = requiresNew.execute(tx -> create(batchId, actor, internalObservations));
        return answer(resolver.resolve(control, 0));
    }

    private QualityControl create(UUID batchId, Actor actor, String internalObservations) {
        BatchProjection batch = batches.findByIdForUpdate(batchId)
                .filter(found -> found.companyId().equals(actor.companyId()))
                .orElseThrow(() -> new NotFoundException(ErrorCodes.BATCH_NOT_FOUND, "El lote no existe."));
        Instant now = clock.instant();
        QualityControl control = QualityControl.start(UUID.randomUUID(), batch, actor.userId(), internalObservations, now);
        controls.insertStarting(control, now.plus(settings.grace()));
        audit.record(actor.userId(), control.companyId(), CONTROL_STARTED, "QualityControl", control.id().toString(),
                Map.of("batchId", batchId.toString(), "controlType", control.controlType().name()));
        return control;
    }

    private StartControlResult answer(ControlResolution resolution) {
        ControlResult result = ControlResult.of(resolution.control());
        return switch (resolution.kind()) {
            case CONFIRMED -> new StartControlResult(result, true);
            case PENDING -> new StartControlResult(result, false);
            case REJECTED -> {
                if (resolution.httpStatus() == 404) {
                    throw new NotFoundException(ErrorCodes.BATCH_NOT_FOUND, "El lote no existe.");
                }
                throw new ConflictException(resolution.code(), "No se pudo iniciar el control: " + resolution.message());
            }
            case DEFECT -> throw new IllegalStateException("Production rechazó el comando de inicio del control " + result.id() + ": "
                    + resolution.httpStatus() + " " + resolution.code());
            case ALREADY_RESOLVED -> {
                if (resolution.control().status() == ControlStatus.CANCELLED) {
                    throw new ConflictException(resolution.control().cancellationReason(), "No se pudo iniciar el control.");
                }
                yield new StartControlResult(result, resolution.control().status() != ControlStatus.STARTING);
            }
        };
    }

    @Override
    public ControlResult get(UUID controlId, Actor actor) {
        return controls.findById(controlId)
                .filter(found -> found.companyId().equals(actor.companyId()))
                .map(ControlResult::of)
                .orElseThrow(() -> new NotFoundException(ErrorCodes.CONTROL_NOT_FOUND, "El control no existe."));
    }
}
