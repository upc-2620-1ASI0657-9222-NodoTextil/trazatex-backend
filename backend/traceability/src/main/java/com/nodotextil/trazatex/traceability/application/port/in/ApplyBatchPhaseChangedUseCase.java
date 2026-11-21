package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchPhaseChangedCommand;

public interface ApplyBatchPhaseChangedUseCase {

    void apply(ApplyBatchPhaseChangedCommand command);
}
