package com.nodotextil.trazatex.production.application;

import java.util.UUID;

public class TransformationNotFoundException extends RuntimeException {

    public TransformationNotFoundException(UUID id) {
        super("Transformation not found: " + id);
    }
}
