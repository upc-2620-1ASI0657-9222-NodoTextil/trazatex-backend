package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.domain.AdminRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminRequestRepository {

	AdminRequest save(AdminRequest request);

	Optional<AdminRequest> findById(UUID id);

	List<AdminRequest> findAll();
}
