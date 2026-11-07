package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyPrivacyCommand;

/** Caso de uso del evento {@code CompanyPrivacyUpdated}: el listener lo llama dentro de la transacción idempotente. */
public interface ApplyCompanyPrivacyUseCase {

    void apply(ApplyCompanyPrivacyCommand command);
}
