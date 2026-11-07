package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.ControlResult;
import com.nodotextil.trazatex.quality.application.dto.FailureResult;
import com.nodotextil.trazatex.quality.application.dto.FinishControlResult;
import com.nodotextil.trazatex.quality.application.port.in.FinishQualityControlUseCase;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityFailureRepositoryPort;
import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.quality.domain.model.ControlStatus;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.quality.domain.model.QualityFailure;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Finaliza un control con el mismo cuidado que el inicio, en tres fases y ninguna dentro de la transacción del llamador:
 * <ol>
 *   <li><b>A</b> ({@code REQUIRES_NEW}): congela el resultado y deja la finalización pendiente (el control sigue {@code IN_PROGRESS}).</li>
 *   <li>Sin transacción: {@code RELEASE} a Production con {@code requestId = controlId}.</li>
 *   <li><b>B</b> ({@code REQUIRES_NEW}): con la confirmación, todo lo demás ({@link ControlFinishResolver}).</li>
 * </ol>
 * Esta clase no es {@code @Transactional} a propósito.
 */
@Service
class ControlFinishService implements FinishQualityControlUseCase {

    private final QualityControlRepositoryPort controls;
    private final QualityFailureRepositoryPort failures;
    private final ControlFinishResolver resolver;
    private final PendingCommandSettings settings;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    ControlFinishService(QualityControlRepositoryPort controls, QualityFailureRepositoryPort failures, ControlFinishResolver resolver,
                         PendingCommandSettings settings, Clock clock, PlatformTransactionManager transactionManager) {
        this.controls = controls;
        this.failures = failures;
        this.resolver = resolver;
        this.settings = settings;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private record Requested(QualityControl control, boolean sendCommand) {
    }

    @Override
    public FinishControlResult finish(UUID controlId, Actor actor) {
        Requested requested = requiresNew.execute(tx -> request(controlId, actor));
        if (!requested.sendCommand()) {
            // Ya estaba pedida: la petición anterior (o el job) es la dueña del comando
            return answer(ControlResolution.pending(requested.control()));
        }
        return answer(resolver.resolve(requested.control(), 0));
    }

    private Requested request(UUID controlId, Actor actor) {
        QualityControl control = controls.findByIdForUpdate(controlId).filter(found -> found.companyId().equals(actor.companyId()))
                .orElseThrow(() -> new NotFoundException(ErrorCodes.CONTROL_NOT_FOUND, "El control no existe."));
        Instant now = clock.instant();
        boolean requested = control.requestFinish(actor.userId(), now);
        if (requested) {
            controls.saveFinishRequest(control, now.plus(settings.grace()));
        }
        return new Requested(control, requested);
    }

    private FinishControlResult answer(ControlResolution resolution) {
        ControlResult result = ControlResult.of(resolution.control());
        return switch (resolution.kind()) {
            case CONFIRMED -> new FinishControlResult(result, FailureResult.of(resolution.failure()), true);
            case PENDING -> new FinishControlResult(result, null, false);
            case REJECTED -> {
                if (resolution.httpStatus() == 404) {
                    throw new NotFoundException(ErrorCodes.BATCH_NOT_FOUND, "El lote no existe.");
                }
                throw new ConflictException(resolution.code(), "No se pudo finalizar el control: " + resolution.message());
            }
            case DEFECT -> throw new IllegalStateException("Production rechazó RELEASE del control " + result.id() + ": " + resolution.httpStatus() + " "
                    + resolution.code());
            case ALREADY_RESOLVED -> {
                QualityControl control = resolution.control();
                if (control.status() == ControlStatus.FINISHED) {
                    yield new FinishControlResult(result, FailureResult.of(openOrLatestFailureOf(control)), true);
                }
                yield new FinishControlResult(result, null, false);
            }
        };
    }

    /** La falla ligada a este control como el último que la originó o reabrió; si el control salió conforme, ninguna. */
    private QualityFailure openOrLatestFailureOf(QualityControl control) {
        return failures.findByControlId(control.id()).orElse(null);
    }
}
