package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.ImpactedBatch;

/** Un aviso de falla derivada {@code PENDING} que un job reclamó, con los intentos que ya lleva. */
public record ClaimedHold(ImpactedBatch impacted, int attempts) {
}
