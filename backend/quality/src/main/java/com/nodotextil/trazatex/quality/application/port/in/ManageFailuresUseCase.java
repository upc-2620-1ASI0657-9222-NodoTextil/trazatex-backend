package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.DiscardFailureResult;
import com.nodotextil.trazatex.quality.application.dto.FailureInfoCommand;
import com.nodotextil.trazatex.quality.application.dto.FailureResult;

import java.util.UUID;

public interface ManageFailuresUseCase {

    FailureResult get(UUID failureId, Actor actor);

    FailureResult complete(UUID failureId, FailureInfoCommand command, Actor actor);

    FailureResult reevaluate(UUID failureId, Actor actor);

    DiscardFailureResult discard(UUID failureId, Actor actor);
}
