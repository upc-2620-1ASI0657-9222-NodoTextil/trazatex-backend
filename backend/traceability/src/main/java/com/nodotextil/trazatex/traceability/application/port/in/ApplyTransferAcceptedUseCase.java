package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyTransferAcceptedCommand;

/** Caso de uso del evento {@code TransferAccepted}: el listener lo llama dentro de la transacción idempotente. */
public interface ApplyTransferAcceptedUseCase {

    void apply(ApplyTransferAcceptedCommand command);
}
