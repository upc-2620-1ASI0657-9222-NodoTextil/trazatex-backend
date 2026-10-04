package com.nodotextil.trazatex.production.application;

import java.util.UUID;

public class MachineNotFoundException extends RuntimeException {

    public MachineNotFoundException(UUID id) {
        super("Machine not found: " + id);
    }
}
