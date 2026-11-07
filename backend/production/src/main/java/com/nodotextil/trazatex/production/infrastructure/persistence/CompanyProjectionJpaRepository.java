package com.nodotextil.trazatex.production.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

interface CompanyProjectionJpaRepository extends JpaRepository<CompanyProjectionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CompanyProjectionEntity c where c.companyId = :id")
    Optional<CompanyProjectionEntity> findByIdForUpdate(@Param("id") UUID id);

    org.springframework.data.domain.Page<CompanyProjectionEntity> findByStatusOrderByBusinessNameAscCompanyIdAsc(CompanyStatus status, Pageable pageable);
}
