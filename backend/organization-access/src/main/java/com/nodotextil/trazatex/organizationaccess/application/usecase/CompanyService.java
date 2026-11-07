package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.CompanyResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.CreateCompanyCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.CreateCompanyResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ManageCompaniesUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.CompanyRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.LicenseRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.OrganizationEventPublisherPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.PrivacyRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.RucValidatorPort;
import com.nodotextil.trazatex.organizationaccess.domain.event.CompanyPrivacyUpdated;
import com.nodotextil.trazatex.organizationaccess.domain.event.CompanyRegistered;
import com.nodotextil.trazatex.organizationaccess.domain.event.CompanyStatusChanged;
import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.organizationaccess.domain.model.Company;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.model.License;
import com.nodotextil.trazatex.organizationaccess.domain.model.RequiredFields;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
class CompanyService implements ManageCompaniesUseCase {

    private final LicenseRepositoryPort licenses;
    private final CompanyRepositoryPort companies;
    private final PrivacyRepositoryPort privacy;
    private final RucValidatorPort rucValidator;
    private final InvitationIssuer invitationIssuer;
    private final OrganizationEventPublisherPort events;
    private final AuditLogPort audit;
    private final Clock clock;

    CompanyService(LicenseRepositoryPort licenses, CompanyRepositoryPort companies, PrivacyRepositoryPort privacy,
                   RucValidatorPort rucValidator, InvitationIssuer invitationIssuer,
                   OrganizationEventPublisherPort events, AuditLogPort audit, Clock clock) {
        this.licenses = licenses;
        this.companies = companies;
        this.privacy = privacy;
        this.rucValidator = rucValidator;
        this.invitationIssuer = invitationIssuer;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public CreateCompanyResult create(CreateCompanyCommand command, Actor actor) {
        new RequiredFields()
                .text("ruc", command.ruc())
                .text("businessName", command.businessName())
                .notEmpty("activities", command.activities())
                .text("adminEmail", command.adminEmail())
                .verify();
        Ruc ruc = new Ruc(command.ruc());
        new Email(command.adminEmail());
        Instant now = clock.instant();

        // La fila de la licencia serializa la creación de empresas: dos simultáneas no superan el límite.
        License license = licenses.findForUpdate()
                .orElseThrow(() -> new NotFoundException("LICENSE_NOT_FOUND", "La licencia no está inicializada."));
        license.ensureCanCreateCompany(companies.count());
        if (companies.existsByRuc(ruc)) {
            throw new ConflictException(ErrorCodes.RUC_ALREADY_REGISTERED, "Ya existe una empresa con ese RUC.");
        }
        rucValidator.ensureActiveAndHabido(ruc);

        Company company = Company.register(UUID.randomUUID(), ruc, command.businessName(), command.activities(), now);
        companies.save(company);
        CompanyPrivacy defaults = CompanyPrivacy.defaults(company.id());
        privacy.save(defaults);
        events.publish(CompanyRegistered.of(company));
        events.publish(CompanyPrivacyUpdated.of(defaults));

        Invitation admin = invitationIssuer.issue(license, Role.COMPANY_ADMIN, company.id(), command.adminEmail(),
                null, null, null, null, actor.userId(), now);
        return new CreateCompanyResult(CompanyResult.from(company), admin.id());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyResult> list() {
        return companies.findAll().stream().map(CompanyResult::from).toList();
    }

    @Override
    public CompanyResult changeStatus(UUID companyId, CompanyStatus status, Actor actor) {
        if (status == null) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Faltan datos obligatorios: status.", List.of("status"));
        }
        Company company = companies.findByIdForUpdate(companyId)
                .orElseThrow(() -> new NotFoundException("La empresa no existe."));
        CompanyStatus previous = company.status();
        if (status == CompanyStatus.INACTIVE) {
            company.deactivate();
        } else {
            company.activate();
        }
        companies.save(company);
        audit.record(actor.userId(), company.id(), "COMPANY_STATUS_CHANGED", "Company", company.id().toString(),
                Map.of("from", previous.name(), "to", company.status().name()));
        events.publish(CompanyStatusChanged.of(company));
        return CompanyResult.from(company);
    }
}
