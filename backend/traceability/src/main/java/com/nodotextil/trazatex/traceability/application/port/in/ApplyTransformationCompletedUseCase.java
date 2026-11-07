package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyTransformationCompletedCommand;

/** Caso de uso del evento {@code TransformationCompleted}: el listener lo llama dentro de la transacción idempotente. */
public interface ApplyTransformationCompletedUseCase {

    void apply(ApplyTransformationCompletedCommand command);
}
