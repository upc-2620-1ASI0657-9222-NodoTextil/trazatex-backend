package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository {

	Invitation save(Invitation invitation);

	Optional<Invitation> findById(UUID id);

	Optional<Invitation> findByToken(String token);

	
	long countPendingNotExpired(UUID companyId, LocalDateTime now);

	
	List<Invitation> findPendingExpiringAtOrBefore(LocalDateTime now);
}
