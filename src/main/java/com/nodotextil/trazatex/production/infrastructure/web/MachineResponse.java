package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.domain.Machine;
import com.nodotextil.trazatex.production.domain.MachineStatus;
import java.util.UUID;

public record MachineResponse(
        UUID id,
        String internalCode,
        String name,
        String type,
        UUID companyId,
        MachineStatus status) {

    static MachineResponse from(Machine machine) {
        return new MachineResponse(
                machine.id(),
                machine.internalCode(),
                machine.name(),
                machine.type(),
                machine.companyId(),
                machine.status());
    }
}
