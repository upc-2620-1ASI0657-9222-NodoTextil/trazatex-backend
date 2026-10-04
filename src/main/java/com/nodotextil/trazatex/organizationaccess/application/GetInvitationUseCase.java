package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.CompanyRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.InvitationRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class GetInvitationUseCase {

	private final InvitationRepository invitations;
	private final CompanyRepository companies;
	private final Clock clock;

	@Autowired
	public GetInvitationUseCase(InvitationRepository invitations, CompanyRepository companies) {
		this(invitations, companies, Clock.systemUTC());
	}

	GetInvitationUseCase(InvitationRepository invitations, CompanyRepository companies,
			Clock clock) {
		this.invitations = invitations;
		this.companies = companies;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public InvitationView execute(String token) {
		Invitation invitation = invitations.findByToken(token)
				.orElseThrow(() -> new OrganizationNotFoundException("Invitation not found"));
		String companyLegalName = companies.findById(invitation.companyId())
				.map(Company::legalName).orElse(null);
		return new InvitationView(invitation, companyLegalName,
				invitation.effectiveStatusAt(LocalDateTime.now(clock)));
	}

	
	public record InvitationView(Invitation invitation, String companyLegalName,
			InvitationStatus status) {
	}
}
