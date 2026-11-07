package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.PhaseCommandResult;
import com.nodotextil.trazatex.production.domain.model.PhaseOperation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code phase_command_log}: idempotencia del comando interno por {@code (batchId, requestId, operation)}. Solo se guardan los
 * comandos aplicados (200); un rechazo no deja rastro.
 */
public interface PhaseCommandLogPort {

    Optional<PhaseCommandResult> find(UUID batchId, String requestId, PhaseOperation operation);

    /** @throws org.springframework.dao.DuplicateKeyException si otra transacción guardó el mismo comando antes */
    void save(UUID batchId, String requestId, PhaseOperation operation, PhaseCommandResult result, Instant at);
}
