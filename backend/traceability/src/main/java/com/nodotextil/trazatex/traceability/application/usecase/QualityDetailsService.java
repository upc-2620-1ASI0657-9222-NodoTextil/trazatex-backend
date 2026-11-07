package com.nodotextil.trazatex.traceability.application.usecase;

import com.nodotextil.trazatex.traceability.application.dto.ApplyFailureCauseCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityTestsCommand;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyQualityDetailsUseCase;
import com.nodotextil.trazatex.traceability.application.port.out.QualityDetailsPort;
import com.nodotextil.trazatex.traceability.domain.model.graph.QualityTestValue;
import org.springframework.stereotype.Service;

/** Guarda en el grafo los valores de las pruebas y la causa de falla que llegan de Quality; la política decide después quién los ve. */
@Service
class QualityDetailsService implements ApplyQualityDetailsUseCase {

    private final QualityDetailsPort details;

    QualityDetailsService(QualityDetailsPort details) {
        this.details = details;
    }

    @Override
    public void applyTests(ApplyQualityTestsCommand command) {
        details.saveTests(command.batchId(), command.occurredAt(), command.tests().stream()
                .map(t -> new QualityTestValue(t.criterionName(), t.expectedValue(), t.obtainedValue(), t.unit(), t.testResult())).toList());
    }

    @Override
    public void applyFailureCause(ApplyFailureCauseCommand command) {
        details.saveFailureCause(command.batchId(), command.occurredAt(), command.visibleCause());
    }
}
