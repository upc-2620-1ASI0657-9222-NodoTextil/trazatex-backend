package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.AddTestCommand;
import com.nodotextil.trazatex.quality.application.dto.ControlResult;
import com.nodotextil.trazatex.quality.application.port.in.AddQualityTestUseCase;
import com.nodotextil.trazatex.quality.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.quality.domain.model.QualityTest;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** US24. No llama a Production, así que es una transacción ordinaria. */
@Service
@Transactional
class ControlTestService implements AddQualityTestUseCase {

    static final String TEST_ADDED = "TEST_ADDED";

    private final QualityControlRepositoryPort controls;
    private final AuditLogPort audit;

    ControlTestService(QualityControlRepositoryPort controls, AuditLogPort audit) {
        this.controls = controls;
        this.audit = audit;
    }

    @Override
    public ControlResult.TestView addTest(UUID controlId, AddTestCommand command, Actor actor) {
        QualityControl control = controls.findByIdForUpdate(controlId).filter(found -> found.companyId().equals(actor.companyId()))
                .orElseThrow(() -> new NotFoundException(ErrorCodes.CONTROL_NOT_FOUND, "El control no existe."));
        QualityTest test = control.addTest(UUID.randomUUID(), command.criterionName(), command.expectedValue(), command.obtainedValue(), command.unit(),
                command.testResult(), command.remarks());
        controls.save(control);
        audit.record(actor.userId(), control.companyId(), TEST_ADDED, "QualityControl", control.id().toString(),
                Map.of("testId", test.id().toString(), "criterion", test.criterionName(), "result", test.testResult().name()));
        return new ControlResult.TestView(test.id(), test.criterionName(), test.expectedValue(), test.obtainedValue(), test.unit(), test.testResult(),
                test.remarks());
    }
}
