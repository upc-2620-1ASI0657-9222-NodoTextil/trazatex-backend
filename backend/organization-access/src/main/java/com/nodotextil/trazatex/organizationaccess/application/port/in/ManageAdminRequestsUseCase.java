package com.nodotextil.trazatex.organizationaccess.application.port.in;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.AdminRequestResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.RequestAdminCommand;
import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequestStatus;

import java.util.List;
import java.util.UUID;

public interface ManageAdminRequestsUseCase {

    /** US07. */
    AdminRequestResult request(RequestAdminCommand command, Actor actor);

    /** El Dueño ve todas; el administrador, las de su empresa. */
    List<AdminRequestResult> list(Actor actor);

    /** US08. {@code decision} es APPROVED o REJECTED. */
    AdminRequestResult decide(UUID requestId, AdminRequestStatus decision, Actor actor);
}
