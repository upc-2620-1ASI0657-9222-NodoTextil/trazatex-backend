package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.QualityFailure;

/** Una falla con el descarte pendiente que un job reclamó, con los intentos que ya lleva. */
public record ClaimedDiscard(QualityFailure failure, int attempts) {
}
