package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.FinishControlResult;

import java.util.UUID;

/** US25, US28, US34: finaliza un control. El control solo cambia de estado cuando Production confirma {@code RELEASE}. */
public interface FinishQualityControlUseCase {

    FinishControlResult finish(UUID controlId, Actor actor);
}
