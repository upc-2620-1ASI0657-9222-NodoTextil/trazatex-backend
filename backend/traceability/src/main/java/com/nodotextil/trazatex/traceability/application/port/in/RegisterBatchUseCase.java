package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.RegisterBatchCommand;

public interface RegisterBatchUseCase {

    void apply(RegisterBatchCommand command);
}
