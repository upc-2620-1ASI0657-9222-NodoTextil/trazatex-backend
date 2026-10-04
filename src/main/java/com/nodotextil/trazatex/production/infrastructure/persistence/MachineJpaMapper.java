package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.Machine;

final class MachineJpaMapper {

    private MachineJpaMapper() {
    }

    static MachineJpaEntity toEntity(Machine machine) {
        return new MachineJpaEntity(
                machine.id(),
                machine.internalCode(),
                machine.name(),
                machine.type(),
                machine.companyId(),
                machine.status());
    }

    static Machine toDomain(MachineJpaEntity entity) {
        return Machine.reconstitute(
                entity.getId(),
                entity.getInternalCode(),
                entity.getName(),
                entity.getType(),
                entity.getCompanyId(),
                entity.getStatus());
    }
}
