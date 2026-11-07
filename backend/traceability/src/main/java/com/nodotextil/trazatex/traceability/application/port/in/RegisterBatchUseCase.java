package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.RegisterBatchCommand;

/** Caso de uso del evento {@code BatchRegistered}: el listener lo llama dentro de la transacción idempotente. */
public interface RegisterBatchUseCase {

    void apply(RegisterBatchCommand command);
}
