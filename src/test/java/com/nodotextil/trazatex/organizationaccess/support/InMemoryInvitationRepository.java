package com.nodotextil.trazatex.organizationaccess.support;

import com.nodotextil.trazatex.organizationaccess.application.port.InvitationRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory double of the invitation port for tests. */
public class InMemoryInvitationRepository implements InvitationRepository {

	private final Map<UUID, Invitation> store = new LinkedHashMap<>();

	@Override
	public Invitation save(Invitation invitation) {
		store.put(invitation.id(), invitation);
		return invitation;
	}

	@Override
	public Optional<Invitation> findById(UUID id) {
		return Optional.ofNullable(store.get(id));
	}

	@Override
	public Optional<Invitation> findByToken(String token) {
		return store.values().stream().filter(invitation -> invitation.token().equals(token))
				.findFirst();
	}

	@Override
	public long countPendingNotExpired(UUID companyId, LocalDateTime now) {
		return store.values().stream()
				.filter(invitation -> invitation.companyId().equals(companyId)
						&& invitation.status() == InvitationStatus.PENDING
						&& invitation.expiresAt().isAfter(now))
				.count();
	}

	@Override
	public List<Invitation> findPendingExpiringAtOrBefore(LocalDateTime now) {
		return store.values().stream()
				.filter(invitation -> invitation.status() == InvitationStatus.PENDING
						&& !invitation.expiresAt().isAfter(now))
				.toList();
	}

	public List<Invitation> all() {
		return List.copyOf(store.values());
	}
}
