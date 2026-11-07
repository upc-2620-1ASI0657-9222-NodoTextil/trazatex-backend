package com.nodotextil.trazatex.traceability.application.dto;

import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;

import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.invalid;
import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

/**
 * {@code BatchPhaseChanged}. Fase, retención y dueño ({@code companyId}: en una aceptación de transferencia es la empresa
 * destino) se aplican solo si {@code batchVersion} es mayor que la guardada.
 */
public record ApplyBatchPhaseChangedCommand(
        UUID eventId, UUID batchId, UUID companyId, BatchPhase newPhase, boolean qualityHold, Long batchVersion) {

    public ApplyBatchPhaseChangedCommand {
        required(eventId, "eventId");
        required(batchId, "batchId");
        required(companyId, "companyId");
        required(newPhase, "newPhase");
        required(batchVersion, "batchVersion");
        if (batchVersion < 0) {
            throw invalid("batchVersion", "no puede ser negativa");
        }
    }
}
