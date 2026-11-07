package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CompanyJpaRepository extends JpaRepository<CompanyEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CompanyEntity c where c.id = :id")
    Optional<CompanyEntity> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByRuc(String ruc);

    List<CompanyEntity> findAllByOrderByCreatedAtAsc();
}
