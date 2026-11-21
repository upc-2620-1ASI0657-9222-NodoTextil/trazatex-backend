package com.nodotextil.trazatex.organizationaccess.application.port.in;

import com.nodotextil.trazatex.organizationaccess.application.dto.AcceptInvitationCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.InvitationDetailResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.InvitationResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.InviteOperatorCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.UserResult;

import java.util.List;
import java.util.UUID;

public interface ManageInvitationsUseCase {

    InvitationResult inviteOperator(InviteOperatorCommand command, Actor actor);

    InvitationResult cancel(UUID invitationId, Actor actor);

    InvitationDetailResult getByToken(String token);

    UserResult accept(String token, AcceptInvitationCommand command);

    com.nodotextil.trazatex.sharedkernel.paging.PageResult<InvitationResult> listIssuedBy(Actor actor, Integer page, Integer size);

    int expireDueInvitations();
}
