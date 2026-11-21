package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class QualityControl {

    private final UUID id;
    private final UUID batchId;
    private final UUID companyId;
    private final ControlType controlType;
    private final UUID inspectorId;
    private final Instant startDate;
    private final String internalObservations;
    private final List<QualityTest> tests = new ArrayList<>();
    private ControlState state;
    private TestResult overallResult;
    private Instant endDate;
    private String cancellationReason;
    private Instant finishRequestedAt;
    private UUID finishRequestedBy;
    private TestResult finishResult;

    private QualityControl(UUID id, UUID batchId, UUID companyId, ControlType controlType, UUID inspectorId, Instant startDate,
                           String internalObservations) {
        this.id = id;
        this.batchId = batchId;
        this.companyId = companyId;
        this.controlType = controlType;
        this.inspectorId = inspectorId;
        this.startDate = startDate;
        this.internalObservations = internalObservations == null || internalObservations.isBlank() ? null : internalObservations.trim();
        this.state = new StartingState();
    }

    public static QualityControl start(UUID id, BatchProjection batch, UUID inspectorId, String internalObservations, Instant now) {
        List<String> missing = new ArrayList<>();
        if (id == null) {
            missing.add("id");
        }
        if (batch == null) {
            missing.add("batch");
        }
        if (inspectorId == null) {
            missing.add("inspectorId");
        }
        if (!missing.isEmpty()) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Faltan datos del control: " + String.join(", ", missing), missing);
        }
        ControlType type = batch.controlTypeToStart();
        return new QualityControl(id, batch.batchId(), batch.companyId(), type, inspectorId, now, internalObservations);
    }

    public void confirmStarted() {
        state = state.confirmStarted();
    }

    public void cancel(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Falta el motivo de la cancelación", List.of("reason"));
        }
        state = state.cancel();
        cancellationReason = reason.trim();
    }

    public QualityTest addTest(UUID testId, String criterionName, String expectedValue, String obtainedValue, String unit, TestResult result,
                               String remarks) {
        state.requireAcceptsTests();
        if (finishPending()) {
            throw new ConflictException(ErrorCodes.CONTROL_FINISH_PENDING,
                    "El control se está finalizando: las pruebas ya no admiten cambios hasta que Production confirme.");
        }
        QualityTest test = new QualityTest(testId, id, criterionName, expectedValue, obtainedValue, unit, result, remarks);
        tests.add(test);
        return test;
    }

    public boolean requestFinish(UUID requestedBy, Instant now) {
        state.requestFinish();
        if (finishPending()) {
            return false;
        }
        if (requestedBy == null) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Falta quién finaliza el control", List.of("requestedBy"));
        }
        if (tests.isEmpty()) {
            throw new ConflictException(ErrorCodes.CONTROL_HAS_NO_TESTS, "No se puede finalizar un control sin pruebas: registra al menos una.");
        }
        finishRequestedAt = now;
        finishRequestedBy = requestedBy;
        finishResult = tests.stream().anyMatch(QualityTest::failed) ? TestResult.FAILED : TestResult.CONFORMING;
        return true;
    }

    public ControlOutcome completeFinish() {
        if (!finishPending()) {
            throw new ConflictException(ErrorCodes.CONTROL_NOT_IN_PROGRESS, "El control no tiene una finalización pendiente.");
        }
        state = state.finish();
        endDate = finishRequestedAt;
        overallResult = finishResult;
        List<UUID> failed = tests.stream().filter(QualityTest::failed).map(QualityTest::id).toList();
        return new ControlOutcome(id, batchId, companyId, controlType, overallResult, startDate, finishRequestedAt, failed);
    }

    public void abortFinish() {
        if (!finishPending()) {
            throw new ConflictException(ErrorCodes.CONTROL_NOT_IN_PROGRESS, "El control no tiene una finalización pendiente.");
        }
        finishRequestedAt = null;
        finishRequestedBy = null;
        finishResult = null;
    }

    public ControlOutcome finish(Instant now) {
        requestFinish(inspectorId, now);
        return completeFinish();
    }

    public boolean finishPending() {
        return state.status() == ControlStatus.IN_PROGRESS && finishRequestedAt != null;
    }

    public static QualityControl restore(UUID id, UUID batchId, UUID companyId, ControlType controlType, ControlStatus status,
                                         TestResult overallResult, UUID inspectorId, Instant startDate, Instant endDate,
                                         String internalObservations, String cancellationReason, List<QualityTest> tests) {
        return restore(id, batchId, companyId, controlType, status, overallResult, inspectorId, startDate, endDate, internalObservations,
                cancellationReason, null, null, null, tests);
    }

    public static QualityControl restore(UUID id, UUID batchId, UUID companyId, ControlType controlType, ControlStatus status,
                                         TestResult overallResult, UUID inspectorId, Instant startDate, Instant endDate,
                                         String internalObservations, String cancellationReason, Instant finishRequestedAt,
                                         UUID finishRequestedBy, TestResult finishResult, List<QualityTest> tests) {
        QualityControl control = new QualityControl(id, batchId, companyId, controlType, inspectorId, startDate, internalObservations);
        control.finishRequestedAt = finishRequestedAt;
        control.finishRequestedBy = finishRequestedBy;
        control.finishResult = finishResult;
        control.state = ControlState.of(status);
        control.overallResult = overallResult;
        control.endDate = endDate;
        control.cancellationReason = cancellationReason;
        control.tests.addAll(tests);
        return control;
    }

    public UUID id() {
        return id;
    }

    public UUID batchId() {
        return batchId;
    }

    public UUID companyId() {
        return companyId;
    }

    public ControlType controlType() {
        return controlType;
    }

    public ControlStatus status() {
        return state.status();
    }

    public TestResult overallResult() {
        return overallResult;
    }

    public UUID inspectorId() {
        return inspectorId;
    }

    public Instant startDate() {
        return startDate;
    }

    public Instant endDate() {
        return endDate;
    }

    public String internalObservations() {
        return internalObservations;
    }

    public String cancellationReason() {
        return cancellationReason;
    }

    public Instant finishRequestedAt() {
        return finishRequestedAt;
    }

    public UUID finishRequestedBy() {
        return finishRequestedBy;
    }

    public TestResult finishResult() {
        return finishResult;
    }

    public List<QualityTest> tests() {
        return Collections.unmodifiableList(tests);
    }
}
