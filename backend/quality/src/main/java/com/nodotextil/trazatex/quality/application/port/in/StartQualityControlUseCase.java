package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.StartControlResult;

import java.util.UUID;

public interface StartQualityControlUseCase {

    StartControlResult start(UUID batchId, Actor actor, String internalObservations);
}
