package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand;

public interface ApplyCompanyRegisteredUseCase {

    void apply(ApplyCompanyRegisteredCommand command);
}
