package com.nodotextil.trazatex.quality.domain.event;

import com.nodotextil.trazatex.quality.domain.model.ControlOutcome;
import com.nodotextil.trazatex.quality.domain.model.ControlType;
import com.nodotextil.trazatex.quality.domain.model.TestResult;

import java.time.Instant;
import java.util.UUID;

/**
 * Tópico {@code quality.control}, clave controlId. Lo consumen analytics y traceability. {@code tests} (agregado en V2) son los valores de
 * las pruebas del control (criterionName, expectedValue, obtainedValue, unit, testResult): Traceability los muestra a otra empresa solo
 * si la dueña comparte {@code QUALITY_TEST_VALUES}.
 */
public record QualityControlCompleted(UUID controlId, UUID batchId, UUID companyId, ControlType controlType, TestResult result,
                                      Instant startedAt, Instant finishedAt, java.util.List<TestValue> tests) implements QualityEvent {

    public record TestValue(String criterionName,String expectedValue,String obtainedValue,String unit,String testResult) { }

    public QualityControlCompleted(UUID controlId,UUID batchId,UUID companyId,ControlType controlType,TestResult result,Instant startedAt,Instant finishedAt) {
        this(controlId,batchId,companyId,controlType,result,startedAt,finishedAt,java.util.List.of());
    }

    public static QualityControlCompleted of(ControlOutcome outcome,java.util.List<com.nodotextil.trazatex.quality.domain.model.QualityTest> tests) {
        return new QualityControlCompleted(outcome.controlId(),outcome.batchId(),outcome.companyId(),outcome.controlType(),outcome.overallResult(),
                outcome.startedAt(),outcome.finishedAt(),tests.stream().map(t -> new TestValue(t.criterionName(),t.expectedValue(),t.obtainedValue(),t.unit(),t.testResult().name())).toList());
    }

    public static final String TYPE = "QualityControlCompleted";

    public static QualityControlCompleted of(ControlOutcome outcome) {
        return new QualityControlCompleted(outcome.controlId(), outcome.batchId(), outcome.companyId(), outcome.controlType(),
                outcome.overallResult(), outcome.startedAt(), outcome.finishedAt());
    }

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return controlId.toString();
    }
}
