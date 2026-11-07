package com.nodotextil.trazatex.quality.application.dto;

import com.nodotextil.trazatex.quality.domain.model.ControlStatus;
import com.nodotextil.trazatex.quality.domain.model.ControlType;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.quality.domain.model.QualityTest;
import com.nodotextil.trazatex.quality.domain.model.TestResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un control de la propia empresa. {@code cancelReason} es el código de error con el que Production rechazó el inicio (solo si
 * {@code status = CANCELLED}). {@code finishPending}: se pidió finalizar y Production aún no confirmó {@code RELEASE}; el control sigue
 * {@code IN_PROGRESS} y sus pruebas no admiten cambios.
 */
public record ControlResult(UUID id, UUID batchId, UUID companyId, ControlType controlType, ControlStatus status, TestResult overallResult,
                            UUID inspectorId, Instant startDate, Instant endDate, String internalObservations, String cancelReason,
                            boolean finishPending, List<TestView> tests) {

    public record TestView(UUID id, String criterionName, String expectedValue, String obtainedValue, String unit, TestResult testResult,
                           String remarks) {

        static TestView of(QualityTest test) {
            return new TestView(test.id(), test.criterionName(), test.expectedValue(), test.obtainedValue(), test.unit(), test.testResult(),
                    test.remarks());
        }
    }

    public static ControlResult of(QualityControl control) {
        return new ControlResult(control.id(), control.batchId(), control.companyId(), control.controlType(), control.status(),
                control.overallResult(), control.inspectorId(), control.startDate(), control.endDate(), control.internalObservations(),
                control.cancellationReason(), control.finishPending(), control.tests().stream().map(TestView::of).toList());
    }
}
