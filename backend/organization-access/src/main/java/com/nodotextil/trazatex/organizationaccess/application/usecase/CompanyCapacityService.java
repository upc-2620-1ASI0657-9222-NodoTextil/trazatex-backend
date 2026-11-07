package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.dto.CompanyCapacityResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ConsultCompanyCapacityUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.out.InvitationRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.LicenseRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.UserRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.License;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Cupo de usuarios de una empresa: los mismos conteos que usa {@link SlotCounter} al invitar, por los puertos de salida (sin SQL en la aplicación). */
@Service
@Transactional(readOnly = true)
class CompanyCapacityService implements ConsultCompanyCapacityUseCase {

    private final LicenseRepositoryPort licenses;
    private final UserRepositoryPort users;
    private final InvitationRepositoryPort invitations;
    private final Clock clock;

    CompanyCapacityService(LicenseRepositoryPort licenses, UserRepositoryPort users, InvitationRepositoryPort invitations, Clock clock) {
        this.licenses = licenses;
        this.users = users;
        this.invitations = invitations;
        this.clock = clock;
    }

    @Override
    public CompanyCapacityResult capacity(UUID companyId) {
        License license = licenses.find().orElseThrow(() -> new NotFoundException("LICENSE_NOT_FOUND", "La licencia no está inicializada."));
        return new CompanyCapacityResult(license.maxUsersPerCompany(), users.countActiveByCompany(companyId),
                invitations.countOccupyingSlots(companyId, clock.instant()));
    }
}
