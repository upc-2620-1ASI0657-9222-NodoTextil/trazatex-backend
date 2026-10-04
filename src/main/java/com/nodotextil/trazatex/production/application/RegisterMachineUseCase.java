package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Machine;
import com.nodotextil.trazatex.production.domain.MachineRepository;
import java.util.UUID;

public class RegisterMachineUseCase {

    private final MachineRepository machineRepository;

    public RegisterMachineUseCase(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    public Machine execute(Command command) {
        Machine machine = Machine.register(
                UUID.randomUUID(),
                command.internalCode(),
                command.name(),
                command.type(),
                command.companyId());
        return machineRepository.save(machine);
    }

    public record Command(String internalCode, String name, String type, UUID companyId) {
    }
}
