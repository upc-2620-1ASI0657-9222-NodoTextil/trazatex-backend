package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.AddTestCommand;
import com.nodotextil.trazatex.quality.application.dto.ControlResult;

import java.util.UUID;

/** US24: registra una prueba en un control {@code IN_PROGRESS} de la propia empresa. */
public interface AddQualityTestUseCase {

    ControlResult.TestView addTest(UUID controlId, AddTestCommand command, Actor actor);
}
