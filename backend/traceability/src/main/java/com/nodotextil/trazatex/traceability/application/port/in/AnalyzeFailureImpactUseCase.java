package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.AnalyzeFailureImpactCommand;

public interface AnalyzeFailureImpactUseCase {

    void apply(AnalyzeFailureImpactCommand command);
}
