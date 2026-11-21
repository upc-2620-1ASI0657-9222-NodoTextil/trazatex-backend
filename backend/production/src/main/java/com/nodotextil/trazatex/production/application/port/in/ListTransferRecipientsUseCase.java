package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.RecipientResult;

public interface ListTransferRecipientsUseCase {

    PageResult<RecipientResult> list(Integer page, Integer size, Actor actor);
}
