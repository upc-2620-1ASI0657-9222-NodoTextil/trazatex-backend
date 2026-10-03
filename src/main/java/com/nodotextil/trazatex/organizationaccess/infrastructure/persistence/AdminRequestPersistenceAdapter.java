package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.AdminRequestRepository;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class AdminRequestPersistenceAdapter implements AdminRequestRepository {

	private final SpringDataAdminRequestRepository repository;

	AdminRequestPersistenceAdapter(SpringDataAdminRequestRepository repository) {
		this.repository = repository;
	}

	@Override
	public AdminRequest save(AdminRequest request) {
		return repository.save(AdminRequestJpaEntity.from(request)).toDomain();
	}

	@Override
	public Optional<AdminRequest> findById(UUID id) {
		return repository.findById(id).map(AdminRequestJpaEntity::toDomain);
	}

	@Override
	public List<AdminRequest> findAll() {
		return repository.findAllByOrderByCreatedAtDesc().stream()
				.map(AdminRequestJpaEntity::toDomain).toList();
	}

	@Override
	public boolean existsPendingByCompanyIdAndEmail(UUID companyId, String email) {
		return repository.existsByCompanyIdAndEmailAndStatus(companyId, email,
				AdminRequestStatus.PENDING);
	}
}
