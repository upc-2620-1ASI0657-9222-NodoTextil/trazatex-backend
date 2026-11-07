package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.model.MachineStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MachineJpaRepository extends JpaRepository<MachineEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MachineEntity m where m.id = :id")
    Optional<MachineEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select count(m) > 0 from MachineEntity m where m.companyId = :companyId and lower(m.internalCode) = lower(:code)")
    boolean existsByCompanyAndCode(@Param("companyId") UUID companyId, @Param("code") String code);

    List<MachineEntity> findByCompanyIdOrderByInternalCodeAscIdAsc(UUID companyId);

    List<MachineEntity> findByCompanyIdAndStatusOrderByInternalCodeAscIdAsc(UUID companyId, MachineStatus status);
}
