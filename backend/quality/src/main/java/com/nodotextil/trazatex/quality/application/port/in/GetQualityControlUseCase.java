package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.ControlResult;

import java.util.UUID;

public interface GetQualityControlUseCase {

    /** Un control de otra empresa es «no existe» ({@code CONTROL_NOT_FOUND}), igual que uno inexistente. */
    ControlResult get(UUID controlId, Actor actor);
}
