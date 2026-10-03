package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.InvitationRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class InvitationPersistenceAdapter implements InvitationRepository {

	private final SpringDataInvitationRepository repository;

	InvitationPersistenceAdapter(SpringDataInvitationRepository repository) {
		this.repository = repository;
	}

	@Override
	public Invitation save(Invitation invitation) {
		return repository.save(InvitationJpaEntity.from(invitation)).toDomain();
	}

	@Override
	public Optional<Invitation> findById(UUID id) {
		return repository.findById(id).map(InvitationJpaEntity::toDomain);
	}

	@Override
	public Optional<Invitation> findByToken(String token) {
		return repository.findByToken(token).map(InvitationJpaEntity::toDomain);
	}

	@Override
	public long countPendingNotExpired(UUID companyId, LocalDateTime now) {
		return repository.countByCompanyIdAndStatusAndExpiresAtAfter(companyId,
				InvitationStatus.PENDING, now);
	}

	@Override
	public List<Invitation> findPendingExpiringAtOrBefore(LocalDateTime now) {
		return repository.findByStatusAndExpiresAtLessThanEqual(InvitationStatus.PENDING, now)
				.stream().map(InvitationJpaEntity::toDomain).toList();
	}
}
