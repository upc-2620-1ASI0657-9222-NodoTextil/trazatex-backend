package com.nodotextil.trazatex.production.domain.model;

/** Lo que devolvió un comando interno la primera vez: se guarda para que repetirlo devuelva exactamente lo mismo. */
public record PhaseCommandResult(BatchPhase phase, boolean qualityHold) {
}
