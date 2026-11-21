package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.AddTestCommand;
import com.nodotextil.trazatex.quality.application.dto.ControlResult;

import java.util.UUID;

public interface AddQualityTestUseCase {

    ControlResult.TestView addTest(UUID controlId, AddTestCommand command, Actor actor);
}
