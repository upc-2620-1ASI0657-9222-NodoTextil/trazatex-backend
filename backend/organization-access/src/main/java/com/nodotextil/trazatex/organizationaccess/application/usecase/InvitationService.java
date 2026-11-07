package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.dto.AcceptInvitationCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.InvitationDetailResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.InvitationResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.InviteOperatorCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.UserResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ManageInvitationsUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.CompanyRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.InvitationRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.LicenseRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.OrganizationEventPublisherPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.PasswordBreachCheckerPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.PasswordHasherPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.UserRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.event.UserAccountUpdated;
import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.organizationaccess.domain.model.Company;
import com.nodotextil.trazatex.organizationaccess.domain.model.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.model.License;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;
import com.nodotextil.trazatex.organizationaccess.domain.service.PasswordPolicy;
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
class InvitationService implements ManageInvitationsUseCase {

    private static final int EXPIRATION_BATCH = 500;

    private final LicenseRepositoryPort licenses;
    private final CompanyRepositoryPort companies;
    private final UserRepositoryPort users;
    private final InvitationRepositoryPort invitations;
    private final InvitationIssuer issuer;
    private final CompanyGuard companyGuard;
    private final PasswordBreachCheckerPort breachChecker;
    private final PasswordHasherPort hasher;
    private final OrganizationEventPublisherPort events;
    private final AuditLogPort audit;
    private final Clock clock;

    InvitationService(LicenseRepositoryPort licenses, CompanyRepositoryPort companies, UserRepositoryPort users,
                      InvitationRepositoryPort invitations, InvitationIssuer issuer, CompanyGuard companyGuard,
                      PasswordBreachCheckerPort breachChecker, PasswordHasherPort hasher,
                      OrganizationEventPublisherPort events, AuditLogPort audit, Clock clock) {
        this.licenses = licenses;
        this.companies = companies;
        this.users = users;
        this.invitations = invitations;
        this.issuer = issuer;
        this.companyGuard = companyGuard;
        this.breachChecker = breachChecker;
        this.hasher = hasher;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public InvitationResult inviteOperator(InviteOperatorCommand command, Actor actor) {
        Instant now = clock.instant();
        // La fila de la empresa serializa quienes usan sus cupos: dos invitaciones por el último cupo no pasan ambas.
        Company company = companyGuard.lockActive(actor.companyId());
        License license = licenses.find()
                .orElseThrow(() -> new NotFoundException("LICENSE_NOT_FOUND", "La licencia no está inicializada."));
        Invitation invitation = issuer.issue(license, Role.OPERATOR, company.id(), command.email(), null,
                command.firstName(), command.lastName(), command.position(), actor.userId(), now);
        return InvitationResult.from(invitation, now);
    }

    @Override
    public InvitationResult cancel(UUID invitationId, Actor actor) {
        Instant now = clock.instant();
        if (actor.role().belongsToCompany()) {
            companyGuard.requireActive(actor.companyId());
        }
        Invitation invitation = invitations.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("La invitación no existe."));
        invitation.cancel(actor.userId(), now);
        invitations.save(invitation);
        return InvitationResult.from(invitation, now);
    }

    @Override
    @Transactional(readOnly = true)
    public com.nodotextil.trazatex.sharedkernel.paging.PageResult<InvitationResult> listIssuedBy(Actor actor, Integer page, Integer size) {
        Instant now = clock.instant();
        return invitations.findPageByIssuer(actor.userId(), com.nodotextil.trazatex.sharedkernel.paging.PageRequest.of(page, size))
                .map(invitation -> InvitationResult.from(invitation, now));
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationDetailResult getByToken(String token) {
        Invitation invitation = invitations.findByToken(token)
                .orElseThrow(() -> new NotFoundException("La invitación no existe."));
        String companyName = invitation.companyId() == null ? null
                : companies.findById(invitation.companyId()).map(Company::businessName).orElse(null);
        return InvitationDetailResult.from(invitation, companyName, clock.instant());
    }

    @Override
    public UserResult accept(String token, AcceptInvitationCommand command) {
        Instant now = clock.instant();
        Invitation invitation = invitations.findByTokenForUpdate(token)
                .orElseThrow(() -> new NotFoundException("La invitación no existe."));
        invitation.ensureUsable(now);
        PasswordPolicy.validate(command.password(), command.passwordConfirmation());
        PasswordBreachCheckerPort.Result breach = breachChecker.check(command.password());
        if (breach == PasswordBreachCheckerPort.Result.COMPROMISED) {
            throw new ValidationException(ErrorCodes.PASSWORD_COMPROMISED,
                    "Esa contraseña aparece en filtraciones de credenciales. Elige una diferente.", List.of("password"));
        }
        if (users.existsByEmail(invitation.email())) {
            throw new ConflictException(ErrorCodes.EMAIL_ALREADY_REGISTERED, "Ese correo ya tiene una cuenta.");
        }

        User user = invitation.accept(UUID.randomUUID(), command.firstName(), command.lastName(),
                hasher.hash(command.password()), now);
        users.save(user);
        invitations.save(invitation);
        if (breach == PasswordBreachCheckerPort.Result.UNAVAILABLE) {
            // ADR-007: si HIBP no responde se acepta la contraseña y se deja constancia
            audit.record(user.id(), user.companyId(), "PASSWORD_BREACH_CHECK_SKIPPED", "User", user.id().toString(),
                    Map.of("reason", "El servicio de contraseñas filtradas no respondió"));
        }
        events.publish(UserAccountUpdated.of(user));
        return UserResult.from(user);
    }

    @Override
    public int expireDueInvitations() {
        Instant now = clock.instant();
        int expired = 0;
        for (Invitation invitation : invitations.findDue(now, EXPIRATION_BATCH)) {
            if (invitation.expire(now)) {
                invitations.save(invitation);
                expired++;
            }
        }
        return expired;
    }
}
