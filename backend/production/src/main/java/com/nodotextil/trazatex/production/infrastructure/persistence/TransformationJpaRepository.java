package com.nodotextil.trazatex.production.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface TransformationJpaRepository extends JpaRepository<TransformationEntity, UUID>, JpaSpecificationExecutor<TransformationEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransformationEntity t where t.id = :id")
    Optional<TransformationEntity> findByIdForUpdate(@Param("id") UUID id);
}
