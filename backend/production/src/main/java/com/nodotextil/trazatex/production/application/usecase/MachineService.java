package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.MachineDetailResult;
import com.nodotextil.trazatex.production.application.dto.MachineResult;
import com.nodotextil.trazatex.production.application.dto.RegisterMachineCommand;
import com.nodotextil.trazatex.production.application.port.in.ManageMachinesUseCase;
import com.nodotextil.trazatex.production.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.production.application.port.out.MachineRepositoryPort;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.MachineStatus;
import com.nodotextil.trazatex.production.domain.model.MachineStatusChange;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** La maquinaria no es una operación productiva: la gestiona su administrador aunque la empresa esté inactiva. */
@Service
@Transactional
class MachineService implements ManageMachinesUseCase {

    private final MachineRepositoryPort machines;
    private final AuditLogPort audit;
    private final Clock clock;

    MachineService(MachineRepositoryPort machines, AuditLogPort audit, Clock clock) {
        this.machines = machines;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public MachineResult register(RegisterMachineCommand command, Actor actor) {
        Instant now = clock.instant();
        Machine machine = Machine.register(UUID.randomUUID(), actor.companyId(), command.internalCode(), command.name(), command.type(), now);
        if (machines.existsByCompanyAndInternalCode(actor.companyId(), machine.internalCode())) {
            throw new ConflictException(ErrorCodes.MACHINE_CODE_ALREADY_EXISTS,
                    "Ya existe una maquinaria con el código " + machine.internalCode() + " en la empresa.");
        }
        machines.save(machine);
        machines.recordStatusChange(new MachineStatusChange(machine.id(), null, MachineStatus.ACTIVE, actor.userId(), now));
        audit.record(actor.userId(), actor.companyId(), "MACHINE_REGISTERED", "Machine", machine.id().toString(),
                BatchService.changes("internalCode", machine.internalCode(), "name", machine.name(), "type", machine.type()));
        return MachineResult.from(machine);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MachineResult> list(MachineStatus status, Actor actor) {
        return machines.findByCompany(actor.companyId(), status).stream().map(MachineResult::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MachineDetailResult get(UUID machineId, Actor actor) {
        Machine machine = machines.findById(machineId).filter(m -> m.companyId().equals(actor.companyId()))
                .orElseThrow(MachineService::notFound);
        return MachineDetailResult.of(machine, machines.statusHistory(machineId));
    }

    @Override
    public MachineResult changeStatus(UUID machineId, MachineStatus status, Actor actor) {
        if (status == null) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Faltan datos obligatorios: status.", List.of("status"));
        }
        Machine machine = machines.findByIdForUpdate(machineId).filter(m -> m.companyId().equals(actor.companyId()))
                .orElseThrow(MachineService::notFound);
        MachineStatusChange change = machine.changeStatus(status, actor.userId(), clock.instant());
        machines.save(machine);
        machines.recordStatusChange(change);
        audit.record(actor.userId(), actor.companyId(), "MACHINE_STATUS_CHANGED", "Machine", machine.id().toString(),
                BatchService.changes("from", change.from().name(), "to", change.to().name()));
        return MachineResult.from(machine);
    }

    private static NotFoundException notFound() {
        return new NotFoundException(ErrorCodes.MACHINE_NOT_FOUND, "La maquinaria no existe.");
    }
}
