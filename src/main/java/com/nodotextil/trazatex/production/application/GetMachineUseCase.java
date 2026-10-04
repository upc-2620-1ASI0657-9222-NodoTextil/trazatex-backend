package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Machine;
import com.nodotextil.trazatex.production.domain.MachineRepository;
import java.util.UUID;

public class GetMachineUseCase {

    private final MachineRepository machineRepository;

    public GetMachineUseCase(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    public Machine execute(UUID id) {
        return machineRepository.findById(id).orElseThrow(() -> new MachineNotFoundException(id));
    }
}
