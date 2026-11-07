package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.dto.PhaseTransitionCommand;
import com.nodotextil.trazatex.production.application.dto.PhaseTransitionResult;
import com.nodotextil.trazatex.production.application.exception.TemporarilyUnavailableException;
import com.nodotextil.trazatex.production.application.port.in.ExecutePhaseTransitionUseCase;
import com.nodotextil.trazatex.production.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.LockWaitLimitPort;
import com.nodotextil.trazatex.production.application.port.out.PhaseCommandLogPort;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.PhaseCommandResult;
import com.nodotextil.trazatex.production.domain.model.PhaseOperation;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Comando interno de Quality (ADR-009). <b>Bloqueo optimista</b> (decisión del informe): el lote se lee sin {@code FOR UPDATE} y
 * {@code @Version} detecta que otra transacción lo modificó; el servicio reintenta la transacción <i>completa</i> —conflicto de
 * versión, o clave duplicada en {@code phase_command_log}— y, si la carrera persiste, responde 503 para que Quality repita el
 * comando con el mismo {@code requestId}. Un 409 es solo un rechazo de negocio: Quality cancela el control cuando lo recibe.
 *
 * <p>Por eso el servicio no es {@code @Transactional}: cada intento abre su propia transacción. Una espera a un bloqueo
 * ({@code lock_timeout}, p. ej. una operación del usuario que tiene el lote con {@code FOR UPDATE}) <b>no</b> se reintenta
 * dentro: ya consumió su tiempo y Quality tiene un timeout de 2 s; se responde 503 de inmediato y Quality reintenta.
 */
@Service
class PhaseTransitionService implements ExecutePhaseTransitionUseCase {

    private static final Logger log = LoggerFactory.getLogger(PhaseTransitionService.class);

    static final int MAX_REQUEST_ID_LENGTH = 64;

    private final TransactionTemplate transaction;
    private final BatchRepositoryPort batches;
    private final BatchChanges batchChanges;
    private final PhaseCommandLogPort commandLog;
    private final LockWaitLimitPort lockWait;
    private final CompanyGuard companyGuard;
    private final AuditLogPort audit;
    private final Clock clock;
    private final Duration lockTimeout;
    private final int attempts;
    private final Duration backoff;

    PhaseTransitionService(PlatformTransactionManager transactionManager, BatchRepositoryPort batches, BatchChanges batchChanges,
                           PhaseCommandLogPort commandLog, LockWaitLimitPort lockWait, CompanyGuard companyGuard, AuditLogPort audit,
                           Clock clock,
                           @Value("${trazatex.phase-transitions.lock-timeout:PT1.5S}") Duration lockTimeout,
                           @Value("${trazatex.phase-transitions.retry-attempts:5}") int attempts,
                           @Value("${trazatex.phase-transitions.retry-backoff:PT0.02S}") Duration backoff) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.batches = batches;
        this.batchChanges = batchChanges;
        this.commandLog = commandLog;
        this.lockWait = lockWait;
        this.companyGuard = companyGuard;
        this.audit = audit;
        this.clock = clock;
        this.lockTimeout = lockTimeout;
        this.attempts = Math.max(1, attempts);
        this.backoff = backoff;
    }

    @Override
    public PhaseTransitionResult execute(UUID batchId, PhaseTransitionCommand command) {
        validate(command);
        RuntimeException lastConflict = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return transaction.execute(status -> applyOnce(batchId, command));
            } catch (OptimisticLockingFailureException | DuplicateKeyException conflict) {
                // Otra transacción ganó la carrera: se reintenta desde cero, y esta vez ve su resultado (o su cambio)
                lastConflict = conflict;
                log.debug("Comando {} {} del lote {}: carrera en el intento {} de {} ({})", command.operation(), command.requestId(),
                        batchId, attempt, attempts, conflict.getClass().getSimpleName());
                pause(attempt);
            } catch (PessimisticLockingFailureException waited) {
                log.warn("Comando {} {} del lote {}: el lote está bloqueado por otra operación; se responde 503 ({})",
                        command.operation(), command.requestId(), batchId, waited.getClass().getSimpleName());
                throw new TemporarilyUnavailableException("El lote está ocupado por otra operación: repita el comando.", waited);
            }
        }
        log.warn("Comando {} {} del lote {}: la carrera no se resolvió tras {} intentos; se responde 503", command.operation(),
                command.requestId(), batchId, attempts);
        throw new TemporarilyUnavailableException("El lote cambió al mismo tiempo: repita el comando.", lastConflict);
    }

    private PhaseTransitionResult applyOnce(UUID batchId, PhaseTransitionCommand command) {
        lockWait.limit(lockTimeout);
        Batch batch = batches.findById(batchId).orElseThrow(() -> new NotFoundException(ErrorCodes.BATCH_NOT_FOUND, "El lote no existe."));
        PhaseOperation operation = command.operation();

        // Repetición: el resultado guardado manda aunque el lote haya cambiado y hoy la operación ya no sería válida
        Optional<PhaseCommandResult> done = commandLog.find(batchId, command.requestId(), operation);
        if (done.isPresent()) {
            return new PhaseTransitionResult(batchId, done.get().phase(), done.get().qualityHold());
        }

        // RF-003: una empresa inactiva es de solo lectura, y Quality no conoce el estado de las empresas. Solo START_EVALUATION
        // inicia trabajo; los demás comandos cierran o protegen un lote y no pueden dejarlo atrapado.
        if (operation == PhaseOperation.START_EVALUATION) {
            companyGuard.requireActive(batch.companyId());
        }

        BatchPhase phaseBefore = batch.phase();
        boolean holdBefore = batch.qualityHold();
        switch (operation) {
            case START_EVALUATION -> batch.startEvaluation();
            case RELEASE -> batch.release(command.qualityHold());
            case DISCARD -> batch.discard();
            case HOLD -> batch.hold();
            case RELEASE_HOLD -> batch.releaseHold();
        }

        if (batch.hasPhaseChanges()) {
            String reason = command.reason() == null || command.reason().isBlank() ? operation.name() : command.reason().trim();
            batchChanges.persistAndPublish(List.of(batch), PhaseChangeCause.of(reason, command.requestId()), null);
            audit.record(command.requestedBy(), batch.companyId(), "BATCH_PHASE_COMMAND", "Batch", batchId.toString(),
                    BatchService.changes("operation", operation.name(), "requestId", command.requestId(),
                            "fromPhase", phaseBefore.name(), "toPhase", batch.phase().name(),
                            "fromQualityHold", holdBefore, "toQualityHold", batch.qualityHold()));
        }
        PhaseCommandResult result = new PhaseCommandResult(batch.phase(), batch.qualityHold());
        commandLog.save(batchId, command.requestId(), operation, result, clock.instant());
        return new PhaseTransitionResult(batchId, result.phase(), result.qualityHold());
    }

    private static void validate(PhaseTransitionCommand command) {
        List<String> missing = new ArrayList<>();
        if (command == null || command.operation() == null) {
            missing.add("operation");
        }
        if (command == null || command.requestId() == null || command.requestId().isBlank()) {
            missing.add("requestId");
        }
        if (command != null && command.operation() == PhaseOperation.RELEASE && command.qualityHold() == null) {
            missing.add("qualityHold");
        }
        if (command != null && command.operation() != null && command.operation() != PhaseOperation.HOLD && command.requestedBy() == null) {
            missing.add("requestedBy");
        }
        if (!missing.isEmpty()) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Faltan datos obligatorios: " + String.join(", ", missing) + ".", missing);
        }
        if (command.requestId().length() > MAX_REQUEST_ID_LENGTH) {
            throw new ValidationException(ErrorCodes.INVALID_REQUEST_ID,
                    "El requestId no puede superar " + MAX_REQUEST_ID_LENGTH + " caracteres.", List.of("requestId"));
        }
    }

    private void pause(int attempt) {
        try {
            // Espera creciente con un poco de azar: varios comandos que chocaron a la vez no vuelven a chocar a la vez
            Thread.sleep(backoff.toMillis() * attempt + java.util.concurrent.ThreadLocalRandom.current().nextLong(backoff.toMillis() + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TemporarilyUnavailableException("Interrumpido mientras se reintentaba el comando.", e);
        }
    }
}
