package com.nodotextil.trazatex.traceability.application.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

/** {@code CompanyPrivacyUpdated}: el mapa completo tal como llegó (campo → SHARED | PRIVATE); se interpreta con tolerancia. */
public record ApplyCompanyPrivacyCommand(UUID eventId, UUID companyId, Map<String, String> fields) {

    public ApplyCompanyPrivacyCommand {
        required(eventId, "eventId");
        required(companyId, "companyId");
        // tolerante: un valor nulo en el mapa no invalida el evento (se trata como PRIVATE al interpretarlo)
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(required(fields, "fields")));
    }
}
