package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.FinishControlResult;

import java.util.UUID;

public interface FinishQualityControlUseCase {

    FinishControlResult finish(UUID controlId, Actor actor);
}
