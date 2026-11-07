package com.nodotextil.trazatex.production.domain.model;

/** Operaciones del comando interno de Quality (ADR-009); coinciden con el {@code CHECK} de {@code phase_command_log.operation}. */
public enum PhaseOperation {
    START_EVALUATION,
    RELEASE,
    DISCARD,
    HOLD,
    RELEASE_HOLD
}
