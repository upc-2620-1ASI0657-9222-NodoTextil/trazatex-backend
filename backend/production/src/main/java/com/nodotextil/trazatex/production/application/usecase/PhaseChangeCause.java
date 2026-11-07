package com.nodotextil.trazatex.production.application.usecase;

/**
 * Qué provocó un cambio de fase o de retención: va en {@code reason} y {@code requestId} de {@code BatchPhaseChanged}.
 * En las operaciones del usuario, {@code reason} es el código de la operación y {@code requestId} el id de la transformación,
 * la transferencia o el lote padre de la división; en los comandos de Quality (04e), los del comando.
 */
record PhaseChangeCause(String reason, String requestId) {

    static final String SPLIT = "SPLIT";
    static final String TRANSFORMATION_STARTED = "TRANSFORMATION_STARTED";
    static final String TRANSFORMATION_COMPLETED = "TRANSFORMATION_COMPLETED";
    static final String TRANSFER_INITIATED = "TRANSFER_INITIATED";
    static final String TRANSFER_ACCEPTED = "TRANSFER_ACCEPTED";
    static final String TRANSFER_REJECTED = "TRANSFER_REJECTED";

    static PhaseChangeCause of(String reason, Object requestId) {
        return new PhaseChangeCause(reason, String.valueOf(requestId));
    }
}
