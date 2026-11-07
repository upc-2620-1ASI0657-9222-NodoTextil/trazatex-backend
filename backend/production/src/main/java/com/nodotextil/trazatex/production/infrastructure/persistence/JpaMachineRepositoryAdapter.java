package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.application.port.out.MachineRepositoryPort;
import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.MachineStatus;
import com.nodotextil.trazatex.production.domain.model.MachineStatusChange;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Maquinaria por JPA; el historial de estados ({@code machine_status_history}) es solo-agregar y va por JDBC. */
@Component
class JpaMachineRepositoryAdapter implements MachineRepositoryPort {

    private final MachineJpaRepository repository;
    private final JdbcClient jdbc;

    JpaMachineRepositoryAdapter(MachineJpaRepository repository, JdbcClient jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override
    public void save(Machine machine) {
        MachineEntity entity = repository.findById(machine.id()).orElseGet(() -> create(machine));
        entity.status = machine.status();
        repository.saveAndFlush(entity);
    }

    private static MachineEntity create(Machine machine) {
        MachineEntity entity = new MachineEntity();
        entity.id = machine.id();
        entity.companyId = machine.companyId();
        entity.internalCode = machine.internalCode();
        entity.name = machine.name();
        entity.type = machine.type();
        entity.createdAt = machine.createdAt();
        return entity;
    }

    @Override
    public void recordStatusChange(MachineStatusChange change) {
        jdbc.sql("""
                        INSERT INTO machine_status_history (machine_id, from_status, to_status, changed_by, changed_at)
                        VALUES (:machineId, :from, :to, :changedBy, :changedAt)
                        """)
                .param("machineId", change.machineId())
                .param("from", change.from() == null ? null : change.from().name(), Types.VARCHAR)
                .param("to", change.to().name())
                .param("changedBy", change.changedBy())
                .param("changedAt", Timestamps.toDb(change.changedAt()))
                .update();
    }

    @Override
    public Optional<Machine> findById(UUID id) {
        return repository.findById(id).map(JpaMachineRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Machine> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(JpaMachineRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByCompanyAndInternalCode(UUID companyId, String internalCode) {
        return repository.existsByCompanyAndCode(companyId, internalCode.trim());
    }

    @Override
    public List<Machine> findByCompany(UUID companyId, MachineStatus status) {
        List<MachineEntity> entities = status == null
                ? repository.findByCompanyIdOrderByInternalCodeAscIdAsc(companyId)
                : repository.findByCompanyIdAndStatusOrderByInternalCodeAscIdAsc(companyId, status);
        return entities.stream().map(JpaMachineRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<MachineStatusChange> statusHistory(UUID machineId) {
        return jdbc.sql("""
                        SELECT machine_id, from_status, to_status, changed_by, changed_at
                        FROM machine_status_history WHERE machine_id = :machineId ORDER BY id
                        """)
                .param("machineId", machineId)
                .query((rs, row) -> new MachineStatusChange(
                        rs.getObject("machine_id", UUID.class),
                        rs.getString("from_status") == null ? null : MachineStatus.valueOf(rs.getString("from_status")),
                        MachineStatus.valueOf(rs.getString("to_status")),
                        rs.getObject("changed_by", UUID.class),
                        Timestamps.fromDb(rs.getObject("changed_at", OffsetDateTime.class))))
                .list();
    }

    private static Machine toDomain(MachineEntity entity) {
        return Machine.restore(entity.id, entity.companyId, entity.internalCode, entity.name, entity.type, entity.status,
                entity.createdAt);
    }
}
