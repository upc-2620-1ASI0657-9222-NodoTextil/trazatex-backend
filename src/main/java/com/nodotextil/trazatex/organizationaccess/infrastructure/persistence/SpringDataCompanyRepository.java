package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCompanyRepository extends JpaRepository<CompanyJpaEntity, UUID> {

	List<CompanyJpaEntity> findAllByOrderByCreatedAtAsc();

	long countByLicenseId(UUID licenseId);

	boolean existsByRuc(String ruc);
}
