package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.MachineStatus;
import com.nodotextil.trazatex.production.domain.model.MachineStatusChange;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** La maquinaria con su historial de estados (US10 esc. 2), del más antiguo al más reciente; el registro tiene {@code from} nulo. */
public record MachineDetailResult(MachineResult machine, List<Change> history) {

    public record Change(MachineStatus from, MachineStatus to, UUID changedBy, Instant changedAt) {
    }

    public static MachineDetailResult of(Machine machine, List<MachineStatusChange> history) {
        return new MachineDetailResult(MachineResult.from(machine),
                history.stream().map(c -> new Change(c.from(), c.to(), c.changedBy(), c.changedAt())).toList());
    }
}
