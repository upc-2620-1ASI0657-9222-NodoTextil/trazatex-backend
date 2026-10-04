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

	/** Pending invitations of a company that have not expired at {@code now}; they hold a seat. */
	long countPendingNotExpired(UUID companyId, LocalDateTime now);

	/** Pending invitations whose expiry is at or before {@code now}. */
	List<Invitation> findPendingExpiringAtOrBefore(LocalDateTime now);
}
