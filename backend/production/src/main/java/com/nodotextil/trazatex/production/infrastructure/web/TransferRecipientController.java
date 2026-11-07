package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.RecipientResult;
import com.nodotextil.trazatex.production.application.dto.Role;
import com.nodotextil.trazatex.production.application.port.in.ListTransferRecipientsUseCase;
import com.nodotextil.trazatex.production.infrastructure.web.security.RequiresRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Las empresas activas a las que se puede transferir, paginadas. Solo llama al caso de uso (regla 5 de CLAUDE.md). */
@RestController
class TransferRecipientController {

    private final ListTransferRecipientsUseCase recipients;

    TransferRecipientController(ListTransferRecipientsUseCase recipients) {
        this.recipients = recipients;
    }

    @GetMapping("/api/transfers/recipients")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    PageResult<RecipientResult> recipients(Actor actor, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return recipients.list(page, size, actor);
    }
}
