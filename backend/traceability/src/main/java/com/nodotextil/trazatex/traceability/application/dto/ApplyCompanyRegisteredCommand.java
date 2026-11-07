package com.nodotextil.trazatex.traceability.application.dto;

import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

/** {@code CompanyRegistered}: de todo el payload solo se usan la empresa y su nombre (la empresa responsable es un dato siempre compartido). */
public record ApplyCompanyRegisteredCommand(UUID eventId, UUID companyId, String businessName) {

    public ApplyCompanyRegisteredCommand {
        required(eventId, "eventId");
        required(companyId, "companyId");
        required(businessName, "businessName");
        businessName = businessName.trim();
    }
}
