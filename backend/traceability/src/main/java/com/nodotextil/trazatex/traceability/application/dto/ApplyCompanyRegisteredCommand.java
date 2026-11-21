package com.nodotextil.trazatex.traceability.application.dto;

import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

public record ApplyCompanyRegisteredCommand(UUID eventId, UUID companyId, String businessName) {

    public ApplyCompanyRegisteredCommand {
        required(eventId, "eventId");
        required(companyId, "companyId");
        required(businessName, "businessName");
        businessName = businessName.trim();
    }
}
