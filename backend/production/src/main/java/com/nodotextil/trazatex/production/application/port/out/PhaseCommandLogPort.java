package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.PhaseCommandResult;
import com.nodotextil.trazatex.production.domain.model.PhaseOperation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PhaseCommandLogPort {

    Optional<PhaseCommandResult> find(UUID batchId, String requestId, PhaseOperation operation);

    void save(UUID batchId, String requestId, PhaseOperation operation, PhaseCommandResult result, Instant at);
}
