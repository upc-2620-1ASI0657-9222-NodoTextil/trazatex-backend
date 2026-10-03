package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository {

	Invitation save(Invitation invitation);

	Optional<Invitation> findById(UUID id);

	Optional<Invitation> findByToken(String token);
}
