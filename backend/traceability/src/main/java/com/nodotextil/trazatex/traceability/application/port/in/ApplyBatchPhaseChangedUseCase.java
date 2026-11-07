package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchPhaseChangedCommand;

/** Caso de uso del evento {@code BatchPhaseChanged}: el listener lo llama dentro de la transacción idempotente. */
public interface ApplyBatchPhaseChangedUseCase {

    void apply(ApplyBatchPhaseChangedCommand command);
}
