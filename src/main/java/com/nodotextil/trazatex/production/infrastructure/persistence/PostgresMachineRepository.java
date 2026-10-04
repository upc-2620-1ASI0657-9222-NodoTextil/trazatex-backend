package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.Machine;
import com.nodotextil.trazatex.production.domain.MachineRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresMachineRepository implements MachineRepository {

    private final SpringDataMachineJpaRepository jpaRepository;

    PostgresMachineRepository(SpringDataMachineJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Machine save(Machine machine) {
        return MachineJpaMapper.toDomain(jpaRepository.save(MachineJpaMapper.toEntity(machine)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Machine> findById(UUID id) {
        return jpaRepository.findById(id).map(MachineJpaMapper::toDomain);
    }
}
