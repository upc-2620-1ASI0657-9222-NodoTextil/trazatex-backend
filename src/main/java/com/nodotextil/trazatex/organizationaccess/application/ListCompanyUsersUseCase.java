package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class ListCompanyUsersUseCase {

	private final OrganizationUserRepository users;

	public ListCompanyUsersUseCase(OrganizationUserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public List<OrganizationUser> execute(UUID adminUserId) {
		OrganizationUser admin = ActingUsers.activeCompanyAdmin(users, adminUserId);
		return users.findByCompanyId(admin.companyId());
	}
}
