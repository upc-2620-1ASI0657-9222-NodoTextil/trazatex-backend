package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

interface LicenseJpaRepository extends JpaRepository<LicenseEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LicenseEntity l")
    List<LicenseEntity> findAllForUpdate();
}
