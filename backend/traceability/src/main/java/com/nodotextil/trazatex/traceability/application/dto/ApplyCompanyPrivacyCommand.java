package com.nodotextil.trazatex.traceability.application.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

public record ApplyCompanyPrivacyCommand(UUID eventId, UUID companyId, Map<String, String> fields) {

    public ApplyCompanyPrivacyCommand {
        required(eventId, "eventId");
        required(companyId, "companyId");
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(required(fields, "fields")));
    }
}
