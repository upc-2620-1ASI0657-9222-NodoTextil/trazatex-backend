package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityStatusCommand;

/** Caso de uso del evento {@code QualityStatusChanged}: el listener lo llama dentro de la transacción idempotente. */
public interface ApplyQualityStatusUseCase {

    void apply(ApplyQualityStatusCommand command);
}
