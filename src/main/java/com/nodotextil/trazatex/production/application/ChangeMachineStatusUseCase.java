package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Machine;
import com.nodotextil.trazatex.production.domain.MachineRepository;
import com.nodotextil.trazatex.production.domain.MachineStatus;
import java.util.UUID;

public class ChangeMachineStatusUseCase {

    private final MachineRepository machineRepository;

    public ChangeMachineStatusUseCase(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    public Machine execute(UUID id, MachineStatus status) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new MachineNotFoundException(id));
        return execute(id, machine.companyId(), status);
    }

    public Machine execute(UUID id, UUID companyId, MachineStatus status) {
        Machine machine = machineRepository.findById(id)
                .orElseThrow(() -> new MachineNotFoundException(id));
        if (!machine.companyId().equals(companyId)) {
            throw new com.nodotextil.trazatex.production.domain.InvalidMachineException(
                    "Machine belongs to another company");
        }
        machine.changeStatus(status);
        return machineRepository.save(machine);
    }
}
