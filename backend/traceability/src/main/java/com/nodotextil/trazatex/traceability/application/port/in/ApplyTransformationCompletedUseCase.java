package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyTransformationCompletedCommand;

public interface ApplyTransformationCompletedUseCase {

    void apply(ApplyTransformationCompletedCommand command);
}
