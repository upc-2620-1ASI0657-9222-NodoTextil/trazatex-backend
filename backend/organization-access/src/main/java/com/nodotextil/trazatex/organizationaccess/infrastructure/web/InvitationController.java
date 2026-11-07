package com.nodotextil.trazatex.organizationaccess.infrastructure.web;

import com.nodotextil.trazatex.organizationaccess.application.dto.AcceptInvitationCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.InvitationDetailResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.InvitationResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.InviteOperatorCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.UserResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ManageInvitationsUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.infrastructure.web.security.PublicEndpoint;
import com.nodotextil.trazatex.organizationaccess.infrastructure.web.security.RequiresRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/invitations")
class InvitationController {

    private final ManageInvitationsUseCase invitations;

    InvitationController(ManageInvitationsUseCase invitations) {
        this.invitations = invitations;
    }

    record InviteOperatorRequest(String firstName, String lastName, String email, String position) {
    }

    record AcceptInvitationRequest(String firstName, String lastName, String password, String passwordConfirmation) {
    }

    @PostMapping("/operators")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.COMPANY_ADMIN)
    InvitationResult inviteOperator(@RequestBody InviteOperatorRequest request, Actor actor) {
        return invitations.inviteOperator(new InviteOperatorCommand(request.firstName(), request.lastName(),
                request.email(), request.position()), actor);
    }

    @PostMapping("/{id}/cancel")
    @RequiresRole({Role.LICENSE_OWNER, Role.COMPANY_ADMIN})
    InvitationResult cancel(@PathVariable UUID id, Actor actor) {
        return invitations.cancel(id, actor);
    }

    @GetMapping("/{token}")
    @PublicEndpoint
    InvitationDetailResult get(@PathVariable String token) {
        return invitations.getByToken(token);
    }

    @PostMapping("/{token}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    @PublicEndpoint
    UserResult accept(@PathVariable String token, @RequestBody AcceptInvitationRequest request) {
        return invitations.accept(token, new AcceptInvitationCommand(request.firstName(), request.lastName(),
                request.password(), request.passwordConfirmation()));
    }
}
