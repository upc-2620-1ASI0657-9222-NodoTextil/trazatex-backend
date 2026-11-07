package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.dto.BootstrapSettings;
import com.nodotextil.trazatex.organizationaccess.application.port.in.BootstrapOwnerUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.out.InvitationRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.LicenseRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.UserRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.License;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Bootstrap del Dueño de Licencia (ADR-013) con la recuperación que pidió el usuario. Nunca crea contraseñas: el Dueño
 * recibe una invitación {@code LICENSE_OWNER} (publica {@code InvitationCreated}) y crea su cuenta al aceptarla.
 *
 * <ol>
 *   <li>Sin licencia: la crea con los límites del entorno y emite la invitación.</li>
 *   <li>Con licencia, sin ningún LICENSE_OWNER ACTIVE ni invitación LICENSE_OWNER PENDING vigente: emite una nueva.</li>
 *   <li>En cualquier otro caso no hace nada. Los límites de una licencia existente no se modifican.</li>
 * </ol>
 * La fila de la licencia se bloquea, de modo que varias instancias arrancando a la vez emiten una sola invitación.
 */
@Service
@Transactional
class OwnerBootstrapService implements BootstrapOwnerUseCase {

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrapService.class);

    private final BootstrapSettings settings;
    private final LicenseRepositoryPort licenses;
    private final UserRepositoryPort users;
    private final InvitationRepositoryPort invitations;
    private final InvitationIssuer issuer;
    private final Clock clock;

    OwnerBootstrapService(BootstrapSettings settings, LicenseRepositoryPort licenses, UserRepositoryPort users,
                          InvitationRepositoryPort invitations, InvitationIssuer issuer, Clock clock) {
        this.settings = settings;
        this.licenses = licenses;
        this.users = users;
        this.invitations = invitations;
        this.issuer = issuer;
        this.clock = clock;
    }

    @Override
    public Outcome run() {
        Instant now = clock.instant();
        Optional<License> existing = licenses.findForUpdate();
        if (existing.isEmpty()) {
            License license = License.create(UUID.randomUUID(), settings.maxCompanies(), settings.maxUsersPerCompany(), now);
            licenses.save(license);
            inviteOwner(license, now);
            log.info("Licencia creada ({} empresas, {} usuarios por empresa) e invitación enviada al Dueño",
                    license.maxCompanies(), license.maxUsersPerCompany());
            return Outcome.LICENSE_CREATED;
        }
        if (users.existsActiveOwner() || invitations.existsLiveOwnerInvitation(now)) {
            return Outcome.NOTHING_TO_DO;
        }
        try {
            inviteOwner(existing.get(), now);
        } catch (ConflictException blocked) {
            log.error("No hay Dueño de Licencia activo ni invitación vigente, pero no se pudo reinvitar a {}: {}",
                    settings.ownerEmail(), blocked.getMessage());
            return Outcome.OWNER_INVITATION_BLOCKED;
        }
        log.warn("No había Dueño de Licencia activo ni invitación vigente: se emitió una nueva invitación para {}",
                settings.ownerEmail());
        return Outcome.OWNER_INVITATION_REISSUED;
    }

    private void inviteOwner(License license, Instant now) {
        issuer.issue(license, Role.LICENSE_OWNER, null, settings.ownerEmail(), settings.ownerName(), null, null, null,
                null, now);
    }
}
