package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchSplitCommand;

/** Caso de uso del evento {@code BatchSplit}: el listener lo llama dentro de la transacción idempotente. */
public interface ApplyBatchSplitUseCase {

    void apply(ApplyBatchSplitCommand command);
}
