package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import com.nodotextil.trazatex.organizationaccess.application.ChangeCompanyStatusUseCase;
import com.nodotextil.trazatex.organizationaccess.application.CreateCompanyUseCase;
import com.nodotextil.trazatex.organizationaccess.application.CreateCompanyUseCase.CreatedCompany;
import com.nodotextil.trazatex.organizationaccess.application.ListCompaniesUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies")
@PreAuthorize("hasRole('LICENSE_OWNER')")
public class CompanyController {

	private final CreateCompanyUseCase createCompany;
	private final ListCompaniesUseCase listCompanies;
	private final ChangeCompanyStatusUseCase changeCompanyStatus;

	public CompanyController(CreateCompanyUseCase createCompany,
			ListCompaniesUseCase listCompanies, ChangeCompanyStatusUseCase changeCompanyStatus) {
		this.createCompany = createCompany;
		this.listCompanies = listCompanies;
		this.changeCompanyStatus = changeCompanyStatus;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreatedCompanyResponse create(@Valid @RequestBody CreateCompanyRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		CreatedCompany created = createCompany.execute(request.legalName(), request.ruc(),
				request.activities(), request.adminEmail(), CurrentUser.id(jwt));
		return new CreatedCompanyResponse(CompanyResponse.from(created.company()),
				created.adminInvitation().id(), created.adminInvitation().expiresAt());
	}

	@GetMapping
	public List<CompanyResponse> list() {
		return listCompanies.execute().stream().map(CompanyResponse::from).toList();
	}

	@PatchMapping("/{id}/status")
	public CompanyResponse changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangeStatusRequest request) {
		return CompanyResponse.from(changeCompanyStatus.execute(id, request.status()));
	}

	public record CreateCompanyRequest(@NotBlank String legalName,
			@NotBlank @Pattern(regexp = "\\d{11}", message = "must have 11 digits") String ruc,
			@NotEmpty Set<@NotBlank String> activities, @NotBlank @Email String adminEmail) {
	}

	public record ChangeStatusRequest(@NotNull CompanyStatus status) {
	}

	public record CompanyResponse(UUID id, String legalName, String ruc, Set<String> activities,
			CompanyStatus status, LocalDateTime createdAt) {

		static CompanyResponse from(Company company) {
			return new CompanyResponse(company.id(), company.legalName(), company.ruc(),
					company.activities(), company.status(), company.createdAt());
		}
	}

	public record CreatedCompanyResponse(CompanyResponse company, UUID adminInvitationId,
			LocalDateTime adminInvitationExpiresAt) {
	}
}
