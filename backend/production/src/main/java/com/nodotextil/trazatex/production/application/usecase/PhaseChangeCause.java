package com.nodotextil.trazatex.production.application.usecase;

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
