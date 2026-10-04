package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataInvitationRepository extends JpaRepository<InvitationJpaEntity, UUID> {

	Optional<InvitationJpaEntity> findByToken(String token);

	long countByCompanyIdAndStatusAndExpiresAtAfter(UUID companyId, InvitationStatus status,
			LocalDateTime now);

	List<InvitationJpaEntity> findByStatusAndExpiresAtLessThanEqual(InvitationStatus status,
			LocalDateTime now);
}
