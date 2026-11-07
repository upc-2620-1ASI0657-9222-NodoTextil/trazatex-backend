package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.DiscardFailureResult;
import com.nodotextil.trazatex.quality.application.dto.FailureInfoCommand;
import com.nodotextil.trazatex.quality.application.dto.FailureResult;

import java.util.UUID;

/** US26 y US27. Una falla de otra empresa es «no existe» ({@code FAILURE_NOT_FOUND}). */
public interface ManageFailuresUseCase {

    FailureResult get(UUID failureId, Actor actor);

    /** US26 esc. 1: causa, cantidad afectada y observaciones. */
    FailureResult complete(UUID failureId, FailureInfoCommand command, Actor actor);

    /** US26 esc. 2: el lote pasa a {@code PENDING_REEVALUATION} y sigue retenido; no llama a Production. */
    FailureResult reevaluate(UUID failureId, Actor actor);

    /** US27: la falla solo queda {@code CONFIRMED} cuando Production confirma {@code DISCARD}. */
    DiscardFailureResult discard(UUID failureId, Actor actor);
}
