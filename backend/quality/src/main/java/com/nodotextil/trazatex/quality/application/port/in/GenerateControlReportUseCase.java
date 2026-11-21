package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.ControlReportDocument;

import java.util.UUID;

public interface GenerateControlReportUseCase {

    ControlReportDocument generate(UUID controlId, Actor actor);
}
