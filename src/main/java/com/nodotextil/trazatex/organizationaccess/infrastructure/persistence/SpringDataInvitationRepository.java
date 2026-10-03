package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataInvitationRepository extends JpaRepository<InvitationJpaEntity, UUID> {

	Optional<InvitationJpaEntity> findByToken(String token);
}
