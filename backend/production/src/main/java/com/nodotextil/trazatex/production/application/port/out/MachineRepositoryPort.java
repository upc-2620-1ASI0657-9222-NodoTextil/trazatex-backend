package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.MachineStatus;
import com.nodotextil.trazatex.production.domain.model.MachineStatusChange;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MachineRepositoryPort {

    void save(Machine machine);

    void recordStatusChange(MachineStatusChange change);

    Optional<Machine> findById(UUID id);

    Optional<Machine> findByIdForUpdate(UUID id);

    boolean existsByCompanyAndInternalCode(UUID companyId, String internalCode);

    List<Machine> findByCompany(UUID companyId, MachineStatus status);

    List<MachineStatusChange> statusHistory(UUID machineId);
}
