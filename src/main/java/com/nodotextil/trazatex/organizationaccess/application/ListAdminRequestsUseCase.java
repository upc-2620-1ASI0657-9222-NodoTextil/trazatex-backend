package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.AdminRequestRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.CompanyRepository;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequestStatus;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists the administrator requests of every company for the license owner (RF-011). */
@Service
public class ListAdminRequestsUseCase {

	private final AdminRequestRepository adminRequests;
	private final CompanyRepository companies;

	public ListAdminRequestsUseCase(AdminRequestRepository adminRequests,
			CompanyRepository companies) {
		this.adminRequests = adminRequests;
		this.companies = companies;
	}

	/** @param status only the requests in this status; {@code null} for all of them */
	@Transactional(readOnly = true)
	public List<AdminRequestView> execute(AdminRequestStatus status) {
		return adminRequests.findAll().stream()
				.filter(request -> status == null || request.status() == status)
				.map(request -> new AdminRequestView(request,
						companies.findById(request.companyId()).map(Company::legalName)
								.orElse(null)))
				.toList();
	}

	public record AdminRequestView(AdminRequest request, String companyLegalName) {
	}
}
