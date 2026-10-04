package com.nodotextil.trazatex.organizationaccess.support;

import com.nodotextil.trazatex.organizationaccess.application.port.AdminRequestRepository;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


public class InMemoryAdminRequestRepository implements AdminRequestRepository {

	private final Map<UUID, AdminRequest> store = new LinkedHashMap<>();

	@Override
	public AdminRequest save(AdminRequest request) {
		store.put(request.id(), request);
		return request;
	}

	@Override
	public Optional<AdminRequest> findById(UUID id) {
		return Optional.ofNullable(store.get(id));
	}

	@Override
	public List<AdminRequest> findAll() {
		return List.copyOf(store.values());
	}

	@Override
	public boolean existsPendingByCompanyIdAndEmail(UUID companyId, String email) {
		return store.values().stream().anyMatch(request -> request.isPending()
				&& request.companyId().equals(companyId) && request.email().equals(email));
	}
}
