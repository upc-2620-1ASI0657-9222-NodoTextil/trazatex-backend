package com.nodotextil.trazatex.production.application;

import java.util.UUID;

public class BatchNotFoundException extends RuntimeException {

    public BatchNotFoundException(UUID id) {
        super("Batch not found: " + id);
    }

    public BatchNotFoundException(String reference) {
        super("Batch not found: " + reference);
    }
}
