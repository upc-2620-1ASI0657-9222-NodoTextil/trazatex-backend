package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.InitiateTransferCommand;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.TransferDirection;
import com.nodotextil.trazatex.production.application.dto.Role;
import com.nodotextil.trazatex.production.application.dto.TransferDetailResult;
import com.nodotextil.trazatex.production.application.dto.TransferResult;
import com.nodotextil.trazatex.production.application.port.in.ManageTransfersUseCase;
import com.nodotextil.trazatex.production.domain.model.TransferStatus;
import com.nodotextil.trazatex.production.infrastructure.web.security.RequiresRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
class TransferController {

    private final ManageTransfersUseCase transfers;

    TransferController(ManageTransfersUseCase transfers) {
        this.transfers = transfers;
    }

    record RejectRequest(String reason) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.OPERATOR)
    TransferResult initiate(@RequestBody InitiateTransferCommand request, Actor actor) {
        return transfers.initiate(request, actor);
    }

    /** {@code direction}: {@code OUTGOING} (enviadas), {@code INCOMING} (recibidas) o {@code ALL} (por defecto). */
    @GetMapping
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    PageResult<TransferResult> search(@RequestParam(required = false) TransferDirection direction,
                                      @RequestParam(required = false) TransferStatus status,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size, Actor actor) {
        return transfers.search(direction, status, page, size, actor);
    }

    @GetMapping("/{id}")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    TransferDetailResult get(@PathVariable UUID id, Actor actor) {
        return transfers.get(id, actor);
    }

    @PostMapping("/{id}/accept")
    @RequiresRole(Role.COMPANY_ADMIN)
    TransferResult accept(@PathVariable UUID id, Actor actor) {
        return transfers.accept(id, actor);
    }

    @PostMapping("/{id}/reject")
    @RequiresRole(Role.COMPANY_ADMIN)
    TransferResult reject(@PathVariable UUID id, @RequestBody RejectRequest request, Actor actor) {
        return transfers.reject(id, request.reason(), actor);
    }
}
