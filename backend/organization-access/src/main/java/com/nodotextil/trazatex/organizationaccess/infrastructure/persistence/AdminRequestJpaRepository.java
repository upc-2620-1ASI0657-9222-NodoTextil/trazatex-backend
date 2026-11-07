package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AdminRequestJpaRepository extends JpaRepository<AdminRequestEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AdminRequestEntity r where r.id = :id")
    Optional<AdminRequestEntity> findByIdForUpdate(@Param("id") UUID id);

    List<AdminRequestEntity> findAllByOrderByCreatedAtDesc();

    List<AdminRequestEntity> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    long countByStatus(AdminRequestStatus status);
}
