package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Maquinaria de una empresa (US10, RF-014). Nace {@code ACTIVE}; solo una {@code ACTIVE} se usa en transformaciones.
 * El código interno es único por empresa (lo garantiza la base). Cada cambio de estado produce un
 * {@link MachineStatusChange} para conservar el historial.
 */
public final class Machine {

    private final UUID id;
    private final UUID companyId;
    private final String internalCode;
    private String name;
    private final String type;
    private MachineStatus status;
    private final Instant createdAt;

    private Machine(UUID id, UUID companyId, String internalCode, String name, String type, MachineStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.companyId = Objects.requireNonNull(companyId, "companyId");
        this.internalCode = internalCode == null ? null : internalCode.trim();
        this.name = name == null ? null : name.trim();
        this.type = type == null ? null : type.trim();
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Machine register(UUID id, UUID companyId, String internalCode, String name, String type, Instant now) {
        new RequiredFields()
                .text("internalCode", internalCode)
                .text("name", name)
                .text("type", type)
                .verify();
        return new Machine(id, companyId, internalCode, name, type, MachineStatus.ACTIVE, now);
    }

    public static Machine restore(UUID id, UUID companyId, String internalCode, String name, String type,
                                  MachineStatus status, Instant createdAt) {
        return new Machine(id, companyId, internalCode, name, type, status, createdAt);
    }

    /** US10 esc. 2: pasa a otro estado de los tres. Repetir el estado actual es 409. */
    public MachineStatusChange changeStatus(MachineStatus target, UUID changedBy, Instant now) {
        Objects.requireNonNull(target, "target");
        if (target == status) {
            throw new ConflictException(ErrorCodes.INVALID_STATUS_TRANSITION,
                    "La maquinaria ya está en estado " + status + ".");
        }
        MachineStatusChange change = new MachineStatusChange(id, status, target, changedBy, now);
        status = target;
        return change;
    }

    /** US10 esc. 3 / US16 esc. 3: 409 si no está {@code ACTIVE} o no es de la empresa que la usa. */
    public void ensureUsableBy(UUID companyId) {
        if (!this.companyId.equals(companyId)) {
            throw new ConflictException(ErrorCodes.MACHINE_COMPANY_MISMATCH,
                    "La maquinaria no pertenece a la empresa que inicia la transformación.");
        }
        if (status != MachineStatus.ACTIVE) {
            throw new ConflictException(ErrorCodes.MACHINE_NOT_ACTIVE,
                    "La maquinaria " + internalCode + " no está activa (" + status + ") y no se puede utilizar.");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
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

    public MachineStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
