package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand;

/** Caso de uso del evento {@code CompanyRegistered}: el listener lo llama dentro de la transacción idempotente. */
public interface ApplyCompanyRegisteredUseCase {

    void apply(ApplyCompanyRegisteredCommand command);
}
