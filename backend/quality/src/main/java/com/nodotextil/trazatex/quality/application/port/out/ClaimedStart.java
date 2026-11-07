package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.QualityControl;

/** Un control con un comando pendiente ({@code STARTING}, o {@code IN_PROGRESS} con la finalización pedida) que un job reclamó, con los intentos que ya lleva. */
public record ClaimedStart(QualityControl control, int attempts) {
}
