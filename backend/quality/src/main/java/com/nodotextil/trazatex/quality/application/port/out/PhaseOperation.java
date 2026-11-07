package com.nodotextil.trazatex.quality.application.port.out;

/** Operaciones del comando interno de Production (docs/architecture/communication.md). */
public enum PhaseOperation {
    START_EVALUATION,
    RELEASE,
    DISCARD,
    HOLD,
    RELEASE_HOLD
}
