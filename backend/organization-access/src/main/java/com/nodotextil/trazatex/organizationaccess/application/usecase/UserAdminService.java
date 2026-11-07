package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.UserResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ManageUsersUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.LicenseRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.OrganizationEventPublisherPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.UserRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.event.UserAccountUpdated;
import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.organizationaccess.domain.model.License;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;
import com.nodotextil.trazatex.organizationaccess.domain.model.UserStatus;
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
class UserAdminService implements ManageUsersUseCase {

    private final UserRepositoryPort users;
    private final LicenseRepositoryPort licenses;
    private final CompanyGuard companyGuard;
    private final SlotCounter slots;
    private final OrganizationEventPublisherPort events;
    private final AuditLogPort audit;
    private final Clock clock;

    UserAdminService(UserRepositoryPort users, LicenseRepositoryPort licenses, CompanyGuard companyGuard,
                     SlotCounter slots, OrganizationEventPublisherPort events, AuditLogPort audit, Clock clock) {
        this.users = users;
        this.licenses = licenses;
        this.companyGuard = companyGuard;
        this.slots = slots;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResult> list(Actor actor) {
        return users.findByCompanyId(actor.companyId()).stream().map(UserResult::from).toList();
    }

    @Override
    public UserResult changeStatus(UUID userId, UserStatus status, Actor actor) {
        if (status == null) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Faltan datos obligatorios: status.", List.of("status"));
        }
        Instant now = clock.instant();
        // Con la fila de la empresa bloqueada: dos inactivaciones no dejan la empresa sin administrador y dos
        // reactivaciones no se reparten el mismo último cupo.
        companyGuard.lockActive(actor.companyId());
        User user = users.findById(userId)
                .filter(candidate -> actor.companyId().equals(candidate.companyId()))
                .orElseThrow(() -> new NotFoundException("El usuario no existe."));
        UserStatus previous = user.status();

        if (status == UserStatus.INACTIVE) {
            user.deactivate(users.countActiveAdminsByCompany(actor.companyId()));
        } else {
            if (!user.isActive()) {
                License license = licenses.find()
                        .orElseThrow(() -> new NotFoundException("LICENSE_NOT_FOUND", "La licencia no está inicializada."));
                license.ensureUserCapacity(slots.usedSlots(actor.companyId(), now));
            }
            user.activate();
        }
        users.save(user);
        audit.record(actor.userId(), user.companyId(), "USER_STATUS_CHANGED", "User", user.id().toString(),
                Map.of("from", previous.name(), "to", user.status().name()));
        events.publish(UserAccountUpdated.of(user));
        return UserResult.from(user);
    }
}
