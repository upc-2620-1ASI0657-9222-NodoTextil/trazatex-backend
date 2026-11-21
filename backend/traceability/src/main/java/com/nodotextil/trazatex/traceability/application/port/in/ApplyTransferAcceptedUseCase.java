package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyTransferAcceptedCommand;

public interface ApplyTransferAcceptedUseCase {

    void apply(ApplyTransferAcceptedCommand command);
}
