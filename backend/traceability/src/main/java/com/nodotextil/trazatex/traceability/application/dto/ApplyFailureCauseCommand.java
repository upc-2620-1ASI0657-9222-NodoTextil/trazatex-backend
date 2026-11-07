package com.nodotextil.trazatex.traceability.application.dto;

import java.time.Instant;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

/**
 * La causa de una falla de calidad ({@code QualityFailureUpdated.cause}; vacía si aún no se completó) con el {@code status} de la falla.
 * {@code occurredAt} decide cuál es la última. Solo una falla {@code CONFIRMED} tiene una causa que Traceability pueda mostrar (I15): en
 * {@code OPEN} la causa todavía es una hipótesis y en {@code RESOLVED} el lote ya se liberó, así que {@link #visibleCause()} la deja vacía.
 */
public record ApplyFailureCauseCommand(UUID eventId, UUID batchId, Instant occurredAt, String status, String cause) {

    public static final String CONFIRMED = "CONFIRMED";

    public ApplyFailureCauseCommand {
        required(eventId, "eventId");
        required(batchId, "batchId");
        required(occurredAt, "occurredAt");
        status = status == null ? "" : status;
        cause = cause == null ? "" : cause;
    }

    /** La causa que se guarda en el grafo: la de la falla confirmada; en cualquier otro estado (o sin estado), vacía. */
    public String visibleCause() {
        return CONFIRMED.equals(status) ? cause : "";
    }
}
