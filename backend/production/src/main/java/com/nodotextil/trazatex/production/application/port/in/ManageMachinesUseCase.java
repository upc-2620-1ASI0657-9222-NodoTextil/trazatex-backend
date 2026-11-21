package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.MachineDetailResult;
import com.nodotextil.trazatex.production.application.dto.MachineResult;
import com.nodotextil.trazatex.production.application.dto.RegisterMachineCommand;
import com.nodotextil.trazatex.production.domain.model.MachineStatus;

import java.util.List;
import java.util.UUID;

public interface ManageMachinesUseCase {

    MachineResult register(RegisterMachineCommand command, Actor actor);

    List<MachineResult> list(MachineStatus status, Actor actor);

    MachineDetailResult get(UUID machineId, Actor actor);

    MachineResult changeStatus(UUID machineId, MachineStatus status, Actor actor);
}
