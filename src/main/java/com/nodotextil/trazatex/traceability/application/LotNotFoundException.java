package com.nodotextil.trazatex.traceability.application;

import java.util.UUID;

public class LotNotFoundException extends RuntimeException {

    public LotNotFoundException(UUID batchId) {
        super("Batch not found: " + batchId);
    }
}
