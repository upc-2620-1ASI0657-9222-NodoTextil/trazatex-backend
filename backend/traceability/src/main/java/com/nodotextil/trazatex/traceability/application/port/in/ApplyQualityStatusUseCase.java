package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityStatusCommand;

public interface ApplyQualityStatusUseCase {

    void apply(ApplyQualityStatusCommand command);
}
