package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.MachineStatus;
import com.nodotextil.trazatex.production.domain.model.MachineStatusChange;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MachineRepositoryPort {

    void save(Machine machine);

    /** Agrega una fila al historial de estados ({@code machine_status_history}); la de registro lleva {@code from} nulo. */
    void recordStatusChange(MachineStatusChange change);

    Optional<Machine> findById(UUID id);

    Optional<Machine> findByIdForUpdate(UUID id);

    /** El código interno es único por empresa sin distinguir mayúsculas. */
    boolean existsByCompanyAndInternalCode(UUID companyId, String internalCode);

    /** Maquinaria de la empresa por código; {@code status} nulo trae todas. */
    List<Machine> findByCompany(UUID companyId, MachineStatus status);

    /** Historial de estados, del más antiguo al más reciente. */
    List<MachineStatusChange> statusHistory(UUID machineId);
}
