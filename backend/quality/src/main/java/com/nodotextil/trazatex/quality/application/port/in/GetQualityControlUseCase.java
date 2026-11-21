package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.ControlResult;

import java.util.UUID;

public interface GetQualityControlUseCase {

    ControlResult get(UUID controlId, Actor actor);
}
