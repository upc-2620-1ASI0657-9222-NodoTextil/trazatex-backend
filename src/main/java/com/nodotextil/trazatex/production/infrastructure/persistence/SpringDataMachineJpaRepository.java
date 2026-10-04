package com.nodotextil.trazatex.production.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMachineJpaRepository extends JpaRepository<MachineJpaEntity, UUID> {
}
