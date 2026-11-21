package com.nodotextil.trazatex.traceability.application.dto;

import java.time.Instant;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

public record ApplyFailureCauseCommand(UUID eventId, UUID batchId, Instant occurredAt, String status, String cause) {

    public static final String CONFIRMED = "CONFIRMED";

    public ApplyFailureCauseCommand {
        required(eventId, "eventId");
        required(batchId, "batchId");
        required(occurredAt, "occurredAt");
        status = status == null ? "" : status;
        cause = cause == null ? "" : cause;
    }

    public String visibleCause() {
        return CONFIRMED.equals(status) ? cause : "";
    }
}
