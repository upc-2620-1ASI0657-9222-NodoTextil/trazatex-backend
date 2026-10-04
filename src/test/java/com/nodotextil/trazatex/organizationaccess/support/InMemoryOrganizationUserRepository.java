package com.nodotextil.trazatex.organizationaccess.support;

import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory double of the user port for tests. */
public class InMemoryOrganizationUserRepository implements OrganizationUserRepository {

	private final Map<UUID, OrganizationUser> store = new LinkedHashMap<>();

	@Override
	public OrganizationUser save(OrganizationUser user) {
		store.put(user.id(), user);
		return user;
	}

	@Override
	public Optional<OrganizationUser> findById(UUID id) {
		return Optional.ofNullable(store.get(id));
	}

	@Override
	public Optional<OrganizationUser> findByEmail(String email) {
		if (email == null) {
			return Optional.empty();
		}
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		return store.values().stream().filter(user -> user.email().equals(normalized)).findFirst();
	}

	@Override
	public List<OrganizationUser> findByCompanyId(UUID companyId) {
		return store.values().stream().filter(user -> companyId.equals(user.companyId())).toList();
	}

	@Override
	public List<OrganizationUser> findActiveByCompanyId(UUID companyId) {
		return findByCompanyId(companyId).stream().filter(OrganizationUser::isActive).toList();
	}

	@Override
	public List<OrganizationUser> findActiveByCompanyIdAndRole(UUID companyId, Role role) {
		return findActiveByCompanyId(companyId).stream().filter(user -> user.role() == role)
				.toList();
	}

	@Override
	public long countActiveByCompanyId(UUID companyId) {
		return findActiveByCompanyId(companyId).size();
	}
}
