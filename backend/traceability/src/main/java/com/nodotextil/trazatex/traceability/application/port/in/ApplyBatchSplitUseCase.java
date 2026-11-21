package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchSplitCommand;

public interface ApplyBatchSplitUseCase {

    void apply(ApplyBatchSplitCommand command);
}
