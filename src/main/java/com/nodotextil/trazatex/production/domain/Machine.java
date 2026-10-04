package com.nodotextil.trazatex.production.domain;

import java.util.Objects;
import java.util.UUID;

public final class Machine {

    private final UUID id;
    private final String internalCode;
    private final String name;
    private final String type;
    private final UUID companyId;
    private MachineStatus status;

    private Machine(
            UUID id,
            String internalCode,
            String name,
            String type,
            UUID companyId,
            MachineStatus status) {
        this.id = Objects.requireNonNull(id, "Machine id is required");
        this.internalCode = requireText(internalCode, "Internal code is required");
        this.name = requireText(name, "Machine name is required");
        this.type = requireText(type, "Machine type is required");
        this.companyId = Objects.requireNonNull(companyId, "Company id is required");
        this.status = Objects.requireNonNull(status, "Machine status is required");
    }

    public static Machine register(
            UUID id, String internalCode, String name, String type, UUID companyId) {
        return new Machine(id, internalCode, name, type, companyId, MachineStatus.ACTIVE);
    }

    public static Machine reconstitute(
            UUID id,
            String internalCode,
            String name,
            String type,
            UUID companyId,
            MachineStatus status) {
        return new Machine(id, internalCode, name, type, companyId, status);
    }

    public void changeStatus(MachineStatus newStatus) {
        status = Objects.requireNonNull(newStatus, "Machine status is required");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidMachineException(message);
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public String internalCode() {
        return internalCode;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public UUID companyId() {
        return companyId;
    }

    public MachineStatus status() {
        return status;
    }
}
