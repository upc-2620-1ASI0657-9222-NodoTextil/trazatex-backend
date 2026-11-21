package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.application.dto.ApplyFailureCauseCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityTestsCommand;

public interface ApplyQualityDetailsUseCase {

    void applyTests(ApplyQualityTestsCommand command);

    void applyFailureCause(ApplyFailureCauseCommand command);
}
