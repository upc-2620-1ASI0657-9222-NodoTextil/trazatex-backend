package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.MachineStatus;

import java.time.Instant;
import java.util.UUID;

public record MachineResult(UUID id, UUID companyId, String internalCode, String name, String type, MachineStatus status,
                            Instant createdAt) {

    public static MachineResult from(Machine machine) {
        return new MachineResult(machine.id(), machine.companyId(), machine.internalCode(), machine.name(), machine.type(),
                machine.status(), machine.createdAt());
    }
}
