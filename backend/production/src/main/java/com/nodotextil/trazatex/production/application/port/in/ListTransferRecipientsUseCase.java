package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.RecipientResult;

/** Las empresas activas a las que se puede transferir (US21), por razón social. */
public interface ListTransferRecipientsUseCase {

    PageResult<RecipientResult> list(Integer page, Integer size, Actor actor);
}
