package com.nodotextil.trazatex.production.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface BatchJpaRepository extends JpaRepository<BatchEntity, UUID>, JpaSpecificationExecutor<BatchEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BatchEntity b where b.id = :id")
    Optional<BatchEntity> findByIdForUpdate(@Param("id") UUID id);

    /** {@code order by id}: el bloqueo se toma siempre en el mismo orden, así que no hay interbloqueos entre lotes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BatchEntity b where b.id in :ids order by b.id")
    List<BatchEntity> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

    Optional<BatchEntity> findByQrCode(String qrCode);

    boolean existsByTraceabilityId(String traceabilityId);

    boolean existsByQrCode(String qrCode);
}
