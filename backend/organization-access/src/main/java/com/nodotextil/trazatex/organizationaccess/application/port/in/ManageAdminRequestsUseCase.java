package com.nodotextil.trazatex.organizationaccess.application.port.in;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.AdminRequestResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.RequestAdminCommand;
import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequestStatus;

import java.util.List;
import java.util.UUID;

public interface ManageAdminRequestsUseCase {

    AdminRequestResult request(RequestAdminCommand command, Actor actor);

    List<AdminRequestResult> list(Actor actor);

    AdminRequestResult decide(UUID requestId, AdminRequestStatus decision, Actor actor);
}
