package com.nodotextil.trazatex.organizationaccess.infrastructure.web;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.InvitationResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ManageInvitationsUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.infrastructure.web.security.RequiresRole;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Las invitaciones que emitió quien consulta (paginadas), con su estado efectivo. Solo llama al caso de uso (regla 5 de CLAUDE.md). */
@RestController
class InvitationQueryController {

    private final ManageInvitationsUseCase invitations;

    InvitationQueryController(ManageInvitationsUseCase invitations) {
        this.invitations = invitations;
    }

    @GetMapping("/api/invitations")
    @RequiresRole({Role.COMPANY_ADMIN, Role.LICENSE_OWNER})
    PageResult<InvitationResult> list(Actor actor, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return invitations.listIssuedBy(actor, page, size);
    }
}
