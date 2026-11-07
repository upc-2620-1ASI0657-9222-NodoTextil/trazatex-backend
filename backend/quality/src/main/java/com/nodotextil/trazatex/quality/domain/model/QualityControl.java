package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Un control de calidad sobre un lote (US23…US25, US28, US34). Su ciclo de vida lo gobierna el patrón State ({@link ControlState}).
 * Nace {@code STARTING}: se guarda antes de mandar {@code START_EVALUATION} a Production y pasa a {@code IN_PROGRESS} con la
 * confirmación (o a {@code CANCELLED} si Production responde 409).
 */
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

    /**
     * Inicia un control: el tipo lo decide el estado de calidad del lote ({@link BatchProjection#controlTypeToStart()}), que también
     * rechaza los lotes que no pueden controlarse. Registra la fecha y hora de inicio (US23 esc. 1).
     */
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

    /** Production confirmó {@code START_EVALUATION}: el control pasa a {@code IN_PROGRESS}. */
    public void confirmStarted() {
        state = state.confirmStarted();
    }

    /**
     * Production rechazó el inicio (409, 404 o un 400 que no se arregla reintentando): el control se cancela y el lote no entra en
     * control. {@code reason} es el código de error con el que respondió Production ({@code INVALID_PHASE_TRANSITION},
     * {@code COMPANY_INACTIVE}...) y queda como motivo de la cancelación.
     */
    public void cancel(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Falta el motivo de la cancelación", List.of("reason"));
        }
        state = state.cancel();
        cancellationReason = reason.trim();
    }

    /** Agrega una prueba (US24). Solo con el control {@code IN_PROGRESS}. */
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

    /**
     * Pide finalizar el control (US25): congela el resultado —todas las pruebas conformes → {@code CONFORMING}; alguna con falla →
     * {@code FAILED}— y las pruebas dejan de admitir cambios. El control sigue {@code IN_PROGRESS} hasta que Production confirme
     * {@code RELEASE} ({@link #completeFinish}); sin pruebas se rechaza. Repetirlo con la finalización ya pedida no cambia nada.
     *
     * @return {@code true} si la solicitud es nueva, {@code false} si ya estaba pedida
     */
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

    /**
     * Production confirmó {@code RELEASE}: el control pasa a {@code FINISHED} con el resultado congelado y la fecha de fin es el instante en que
     * el usuario lo finalizó. Devuelve el resultado, que es lo único que cambia el estado de calidad del lote.
     */
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

    /** Production rechazó {@code RELEASE} (409, 404 o un 400): el control sigue {@code IN_PROGRESS}, las pruebas vuelven a admitir cambios. */
    public void abortFinish() {
        if (!finishPending()) {
            throw new ConflictException(ErrorCodes.CONTROL_NOT_IN_PROGRESS, "El control no tiene una finalización pendiente.");
        }
        finishRequestedAt = null;
        finishRequestedBy = null;
        finishResult = null;
    }

    /** Atajo para pedir y confirmar de una vez (pruebas de dominio); la aplicación pasa por {@link #requestFinish} y {@link #completeFinish}. */
    public ControlOutcome finish(Instant now) {
        requestFinish(inspectorId, now);
        return completeFinish();
    }

    /** Se pidió finalizar y Production aún no confirmó {@code RELEASE}. */
    public boolean finishPending() {
        return state.status() == ControlStatus.IN_PROGRESS && finishRequestedAt != null;
    }

    /**
     * Reconstruye un control guardado. El estado viene de {@code status}; las pruebas ya registradas se pasan tal cual (no se vuelven
     * a validar: salieron de {@link #addTest}).
     */
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

    /** Siempre privadas: no viajan a otras empresas. */
    public String internalObservations() {
        return internalObservations;
    }

    /** El código de error de Production que canceló el inicio; {@code null} si el control no se canceló. */
    public String cancellationReason() {
        return cancellationReason;
    }

    /** Cuándo y quién pidió finalizar; el resultado congelado en ese momento. Nulos si nunca se pidió. */
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
