package com.nodotextil.trazatex.traceability.domain;

import java.util.UUID;

public record AffectedLot(UUID batchId, Reason reason) {

    public enum Reason {
        DIVISION_BRANCH,
        TRANSFORMATION_OUTPUT
    }
}
