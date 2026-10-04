package com.nodotextil.trazatex.production.application;

import java.util.UUID;

public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException(UUID id) {
        super("Transfer not found: " + id);
    }
}
