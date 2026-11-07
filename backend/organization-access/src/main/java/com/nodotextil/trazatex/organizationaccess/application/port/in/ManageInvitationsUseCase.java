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

    /** US06. */
    InvitationResult inviteOperator(InviteOperatorCommand command, Actor actor);

    /** US41. */
    InvitationResult cancel(UUID invitationId, Actor actor);

    /** Público. */
    InvitationDetailResult getByToken(String token);

    /** US04. Público. */
    UserResult accept(String token, AcceptInvitationCommand command);

    /**
     * Una página de las invitaciones que emitió quien consulta (COMPANY_ADMIN o LICENSE_OWNER), de la más reciente a la más antigua, con su
     * estado efectivo: una PENDING vencida sale EXPIRED. {@code page} desde 0, {@code size} por defecto 20 y máximo 100 (RNF-03).
     */
    com.nodotextil.trazatex.sharedkernel.paging.PageResult<InvitationResult> listIssuedBy(Actor actor, Integer page, Integer size);

    /** Marca EXPIRED las PENDING vencidas (lo invoca el job). Devuelve cuántas. */
    int expireDueInvitations();
}
