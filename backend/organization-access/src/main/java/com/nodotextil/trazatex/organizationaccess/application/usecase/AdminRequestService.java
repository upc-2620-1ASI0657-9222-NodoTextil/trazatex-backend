package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.AdminRequestResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.RequestAdminCommand;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ManageAdminRequestsUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.out.AdminRequestRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.LicenseRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.OrganizationEventPublisherPort;
import com.nodotextil.trazatex.organizationaccess.domain.event.AdminRequestResolved;
import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequestStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.model.License;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
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
class AdminRequestService implements ManageAdminRequestsUseCase {

    private final AdminRequestRepositoryPort requests;
    private final LicenseRepositoryPort licenses;
    private final CompanyGuard companyGuard;
    private final InvitationIssuer issuer;
    private final OrganizationEventPublisherPort events;
    private final AuditLogPort audit;
    private final Clock clock;

    AdminRequestService(AdminRequestRepositoryPort requests, LicenseRepositoryPort licenses, CompanyGuard companyGuard,
                        InvitationIssuer issuer, OrganizationEventPublisherPort events, AuditLogPort audit, Clock clock) {
        this.requests = requests;
        this.licenses = licenses;
        this.companyGuard = companyGuard;
        this.issuer = issuer;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public AdminRequestResult request(RequestAdminCommand command, Actor actor) {
        companyGuard.requireActive(actor.companyId());
        AdminRequest request = AdminRequest.create(UUID.randomUUID(), actor.companyId(), actor.userId(),
                command.fullName(), command.email(), command.position(), clock.instant());
        requests.save(request);
        events.publish(new com.nodotextil.trazatex.organizationaccess.domain.event.AdminRequestSubmitted(request.id(), request.companyId()));
        return AdminRequestResult.from(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminRequestResult> list(Actor actor) {
        List<AdminRequest> found = actor.role() == Role.LICENSE_OWNER
                ? requests.findAll()
                : requests.findByCompanyId(actor.companyId());
        return found.stream().map(AdminRequestResult::from).toList();
    }

    @Override
    public AdminRequestResult decide(UUID requestId, AdminRequestStatus decision, Actor actor) {
        if (decision != AdminRequestStatus.APPROVED && decision != AdminRequestStatus.REJECTED) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS,
                    "La decisión debe ser APPROVED o REJECTED.", List.of("decision"));
        }
        Instant now = clock.instant();
        AdminRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new NotFoundException("La solicitud no existe."));

        UUID invitationId = null;
        if (decision == AdminRequestStatus.APPROVED) {
            request.approve(actor.userId(), now);
            // Con la fila de la empresa bloqueada; si no hay cupo (409) la transacción se revierte y la solicitud sigue PENDING.
            companyGuard.lockCompany(request.companyId());
            License license = licenses.find()
                    .orElseThrow(() -> new NotFoundException("LICENSE_NOT_FOUND", "La licencia no está inicializada."));
            Invitation invitation = issuer.issue(license, Role.COMPANY_ADMIN, request.companyId(),
                    request.email().value(), request.fullName(), null, null, request.position(), actor.userId(), now);
            invitationId = invitation.id();
        } else {
            request.reject(actor.userId(), now);
        }
        requests.save(request);
        audit.record(actor.userId(), request.companyId(), "ADMIN_REQUEST_" + decision.name(), "AdminRequest",
                request.id().toString(), Map.of("decision", decision.name(), "requestedBy", request.requestedBy().toString()));
        events.publish(AdminRequestResolved.of(request));
        return AdminRequestResult.from(request, invitationId);
    }
}
