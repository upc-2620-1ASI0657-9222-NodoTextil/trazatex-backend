package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.InitiateTransferCommand;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.TransferDirection;
import com.nodotextil.trazatex.production.application.dto.TransferDetailResult;
import com.nodotextil.trazatex.production.application.dto.TransferResult;
import com.nodotextil.trazatex.production.domain.model.TransferStatus;

import java.util.UUID;

/** US21 iniciar, US22 resolver. */
public interface ManageTransfersUseCase {

    TransferResult initiate(InitiateTransferCommand command, Actor actor);

    TransferDetailResult get(UUID transferId, Actor actor);

    PageResult<TransferResult> search(TransferDirection direction, TransferStatus status, Integer page, Integer size, Actor actor);

    TransferResult accept(UUID transferId, Actor actor);

    TransferResult reject(UUID transferId, String reason, Actor actor);
}
