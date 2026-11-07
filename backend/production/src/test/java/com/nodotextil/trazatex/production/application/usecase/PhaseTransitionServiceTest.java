package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.dto.PhaseTransitionCommand;
import com.nodotextil.trazatex.production.application.dto.PhaseTransitionResult;
import com.nodotextil.trazatex.production.application.exception.TemporarilyUnavailableException;
import com.nodotextil.trazatex.production.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.CompanyProjectionRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.LockWaitLimitPort;
import com.nodotextil.trazatex.production.application.port.out.PhaseCommandLogPort;
import com.nodotextil.trazatex.production.application.port.out.ProductionEventPublisherPort;
import com.nodotextil.trazatex.production.domain.event.BatchPhaseChanged;
import com.nodotextil.trazatex.production.domain.event.ProductionEvent;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.CompanyProjection;
import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import com.nodotextil.trazatex.production.domain.model.PhaseCommandResult;
import com.nodotextil.trazatex.production.domain.model.PhaseOperation;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import com.nodotextil.trazatex.sharedkernel.testsupport.MutableClock;
import com.nodotextil.trazatex.sharedkernel.testsupport.RecordingTransactionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.OPERATOR;
import static com.nodotextil.trazatex.production.domain.Fixtures.cotton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhaseTransitionServiceTest {

    private static final Instant START = Instant.parse("2026-10-20T12:00:00Z");
    private static final UUID BATCH = UUID.fromString("00000000-0000-0000-0000-00000000ba01");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    /** Estado actual del lote en la "base": cada findById devuelve una copia nueva, como cada transacción. */
    private BatchPhase phase = BatchPhase.AVAILABLE;
    private boolean hold = false;
    private boolean finalProduct = false;
    private boolean batchExists = true;

    private final BatchRepositoryPort batches = mock(BatchRepositoryPort.class);
    private final ProductionEventPublisherPort events = mock(ProductionEventPublisherPort.class);
    private final AuditLogPort audit = mock(AuditLogPort.class);
    private final LockWaitLimitPort lockWait = mock(LockWaitLimitPort.class);
    private final Map<String, PhaseCommandResult> log = new HashMap<>();
    private final Map<UUID, CompanyProjection> companies = new HashMap<>();
    private final List<String> logSaves = new ArrayList<>();
    private int logSavesToFailWithDuplicate = 0;

    private final PhaseCommandLogPort commandLog = new PhaseCommandLogPort() {
        @Override
        public Optional<PhaseCommandResult> find(UUID batchId, String requestId, PhaseOperation operation) {
            return Optional.ofNullable(log.get(requestId + "|" + operation));
        }

        @Override
        public void save(UUID batchId, String requestId, PhaseOperation operation, PhaseCommandResult result, Instant at) {
            if (logSavesToFailWithDuplicate-- > 0) {
                log.put(requestId + "|" + operation, new PhaseCommandResult(BatchPhase.IN_QUALITY_CONTROL, false));   // la ganó otra transacción
                throw new DuplicateKeyException("phase_command_log_pkey");
            }
            log.put(requestId + "|" + operation, result);
            logSaves.add(requestId + "|" + operation);
        }
    };

    private final CompanyProjectionRepositoryPort companyStore = new CompanyProjectionRepositoryPort() {
        @Override
        public void save(CompanyProjection company) {
            companies.put(company.companyId(), company);
        }

        @Override
        public Optional<CompanyProjection> findById(UUID companyId) {
            return Optional.ofNullable(companies.get(companyId));
        }

        @Override
        public Optional<CompanyProjection> findByIdForUpdate(UUID companyId) {
            return findById(companyId);
        }

        @Override
        public com.nodotextil.trazatex.production.application.port.out.Page<CompanyProjection> findActive(int page, int size) {
            throw new UnsupportedOperationException("no lo usa esta prueba");
        }
    };

    private final PhaseTransitionService service;

    PhaseTransitionServiceTest() {
        companies.put(COMPANY, CompanyProjection.of(COMPANY, "Hilos SAC", CompanyStatus.ACTIVE, START));
        when(batches.findById(BATCH)).thenAnswer(call -> batchExists ? Optional.of(freshBatch()) : Optional.empty());
        when(batches.saveAll(any())).thenAnswer(PhaseTransitionServiceTest::savedAtVersion);
        service = new PhaseTransitionService(new RecordingTransactionManager(), batches, new BatchChanges(batches, events), commandLog,
                lockWait, new CompanyGuard(companyStore), audit, new MutableClock(START), Duration.ofMillis(1500), 3, Duration.ZERO);
    }

    /** La versión que devuelve la base tras guardar (la que viaja como batchVersion). */
    private static final long SAVED_VERSION = 8L;

    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID, Long> savedAtVersion(org.mockito.invocation.InvocationOnMock call) {
        java.util.Map<UUID, Long> versions = new java.util.LinkedHashMap<>();
        ((java.util.Collection<Batch>) call.getArgument(0)).forEach(batch -> versions.put(batch.id(), SAVED_VERSION));
        return versions;
    }

    private Batch freshBatch() {
        return com.nodotextil.trazatex.production.domain.Fixtures.batch(phase, hold, finalProduct, Quantity.ofKg("10"), cotton(), BATCH, QualityStatus.NOT_REVIEWED);
    }

    private static PhaseTransitionCommand command(PhaseOperation operation, String requestId) {
        return new PhaseTransitionCommand(operation, requestId, operation == PhaseOperation.RELEASE ? Boolean.FALSE : null, null, USER);
    }

    private List<ProductionEvent> publishedEvents() {
        ArgumentCaptor<ProductionEvent> captor = ArgumentCaptor.forClass(ProductionEvent.class);
        verify(events, org.mockito.Mockito.atLeast(0)).publish(captor.capture());
        return captor.getAllValues();
    }

    // ---- la tabla completa: 5 operaciones × 7 fases × retención × producto final ----

    static Stream<Arguments> everyOperationInEveryState() {
        return Stream.of(PhaseOperation.values()).flatMap(op -> Stream.of(BatchPhase.values()).flatMap(phase ->
                Stream.of(false, true).flatMap(hold -> Stream.of(false, true).map(finalProduct -> Arguments.of(op, phase, hold, finalProduct)))));
    }

    @ParameterizedTest(name = "{0} sobre {1}, retención={2}, final={3}")
    @MethodSource("everyOperationInEveryState")
    void eachOperationIsAcceptedOrRejectedExactlyAsTheTransitionTableSays(PhaseOperation operation, BatchPhase from, boolean heldBefore,
                                                                         boolean isFinal) {
        phase = from;
        hold = heldBefore;
        finalProduct = isFinal;
        boolean accepted = switch (operation) {
            case START_EVALUATION -> from == BatchPhase.AVAILABLE && !isFinal;
            case RELEASE -> from == BatchPhase.IN_QUALITY_CONTROL;
            case DISCARD, HOLD, RELEASE_HOLD -> from == BatchPhase.AVAILABLE;
        };

        if (!accepted) {
            String code = operation == PhaseOperation.START_EVALUATION && from == BatchPhase.AVAILABLE ? ErrorCodes.BATCH_IS_FINAL_PRODUCT
                    : ErrorCodes.INVALID_PHASE_TRANSITION;
            assertThatThrownBy(() -> service.execute(BATCH, command(operation, "r-1")))
                    .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo(code));
            assertThat(log).as("un rechazo no se guarda").isEmpty();
            verify(events, never()).publish(any());
            verify(audit, never()).record(any(), any(), any(), any(), any(), any());
            return;
        }

        PhaseTransitionResult result = service.execute(BATCH, command(operation, "r-1"));

        BatchPhase expectedPhase = switch (operation) {
            case START_EVALUATION -> BatchPhase.IN_QUALITY_CONTROL;
            case RELEASE -> BatchPhase.AVAILABLE;
            case DISCARD -> BatchPhase.DISCARDED;
            case HOLD, RELEASE_HOLD -> from;
        };
        boolean expectedHold = switch (operation) {
            case START_EVALUATION, DISCARD -> heldBefore;
            case RELEASE -> false;     // el comando de la prueba trae qualityHold=false
            case HOLD -> true;
            case RELEASE_HOLD -> false;
        };
        assertThat(result).isEqualTo(new PhaseTransitionResult(BATCH, expectedPhase, expectedHold));
        assertThat(log.get("r-1|" + operation)).isEqualTo(new PhaseCommandResult(expectedPhase, expectedHold));
        boolean changed = expectedPhase != from || expectedHold != heldBefore;
        assertThat(publishedEvents()).as("exactamente un BatchPhaseChanged si cambió fase o retención, y nada si no").hasSize(changed ? 1 : 0);
        if (changed) {
            assertThat(publishedEvents().get(0)).isEqualTo(new BatchPhaseChanged(BATCH, COMPANY, from, expectedPhase, expectedHold,
                    operation.name(), "r-1", SAVED_VERSION));
            verify(audit).record(any(), any(), any(), any(), any(), any());
        } else {
            verify(audit, never()).record(any(), any(), any(), any(), any(), any());
        }
    }

    // ---- repetición idempotente ----

    @Test
    void repeatingACommandReturnsTheStoredResultEvenIfTheBatchChangedAndTheOperationWouldNowBeInvalid() {
        PhaseTransitionResult first = service.execute(BATCH, command(PhaseOperation.START_EVALUATION, "control-1"));
        assertThat(first.phase()).isEqualTo(BatchPhase.IN_QUALITY_CONTROL);
        phase = BatchPhase.DISCARDED;                                   // el lote cambió después
        companies.put(COMPANY, CompanyProjection.of(COMPANY, "Hilos SAC", CompanyStatus.INACTIVE, START));   // y la empresa se inactivó

        PhaseTransitionResult again = service.execute(BATCH, command(PhaseOperation.START_EVALUATION, "control-1"));

        assertThat(again).isEqualTo(first);
        assertThat(publishedEvents()).as("la repetición no publica nada").hasSize(1);
    }

    @Test
    void theSameRequestIdWithAnotherOperationIsAnotherCommand() {
        service.execute(BATCH, command(PhaseOperation.START_EVALUATION, "control-1"));
        phase = BatchPhase.IN_QUALITY_CONTROL;

        PhaseTransitionResult release = service.execute(BATCH, command(PhaseOperation.RELEASE, "control-1"));

        assertThat(release.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(log).containsKeys("control-1|START_EVALUATION", "control-1|RELEASE");
        assertThat(publishedEvents()).hasSize(2);
    }

    @Test
    void theSameOperationWithAnotherRequestIdIsAnotherCommand() {
        service.execute(BATCH, command(PhaseOperation.HOLD, "falla-1"));
        hold = true;

        PhaseTransitionResult second = service.execute(BATCH, command(PhaseOperation.HOLD, "falla-2"));

        assertThat(second.qualityHold()).isTrue();
        assertThat(publishedEvents()).as("la segunda no cambia nada: 200 sin evento").hasSize(1);
        assertThat(log).containsKeys("falla-1|HOLD", "falla-2|HOLD");
    }

    @Test
    void aReleaseRepeatedWithAnotherQualityHoldReturnsTheOriginalResult() {
        phase = BatchPhase.IN_QUALITY_CONTROL;
        PhaseTransitionResult first = service.execute(BATCH, new PhaseTransitionCommand(PhaseOperation.RELEASE, "c-1", true, null, USER));
        phase = BatchPhase.AVAILABLE;
        hold = true;

        PhaseTransitionResult again = service.execute(BATCH, new PhaseTransitionCommand(PhaseOperation.RELEASE, "c-1", false, null, USER));

        assertThat(first.qualityHold()).isTrue();
        assertThat(again).isEqualTo(first);
    }

    @Test
    void holdAndReleaseHoldWithoutChangeAreRecordedSoRepeatingThemReturnsTheSame() {
        hold = true;
        PhaseTransitionResult first = service.execute(BATCH, command(PhaseOperation.HOLD, "f-1"));
        hold = false;       // alguien lo liberó después

        PhaseTransitionResult again = service.execute(BATCH, command(PhaseOperation.HOLD, "f-1"));

        assertThat(first.qualityHold()).isTrue();
        assertThat(again).isEqualTo(first);
        assertThat(logSaves).containsExactly("f-1|HOLD");
        verify(events, never()).publish(any());
    }

    // ---- validación (400) ----

    private static PhaseTransitionCommand with(PhaseOperation op, String requestId, Boolean qualityHold, UUID requestedBy) {
        return new PhaseTransitionCommand(op, requestId, qualityHold, null, requestedBy);
    }

    @Test
    void missingFieldsAreListedAndNothingIsRead() {
        assertThatThrownBy(() -> service.execute(BATCH, with(null, null, null, null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCodes.MISSING_FIELDS);
                    assertThat(e.details()).containsExactly("operation", "requestId");
                });
        assertThatThrownBy(() -> service.execute(BATCH, with(PhaseOperation.DISCARD, "  ", null, USER)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.details()).containsExactly("requestId"));
        assertThatThrownBy(() -> service.execute(BATCH, null)).isInstanceOf(ValidationException.class);
        verify(batches, never()).findById(any());
        verify(lockWait, never()).limit(any());
    }

    @Test
    void releaseWithoutQualityHoldIsRejected() {
        phase = BatchPhase.IN_QUALITY_CONTROL;

        assertThatThrownBy(() -> service.execute(BATCH, with(PhaseOperation.RELEASE, "c-1", null, USER)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCodes.MISSING_FIELDS);
                    assertThat(e.details()).containsExactly("qualityHold");
                });
        assertThat(log).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = PhaseOperation.class, names = {"START_EVALUATION", "RELEASE", "DISCARD", "RELEASE_HOLD"})
    void requestedByIsRequiredExceptForHold(PhaseOperation operation) {
        assertThatThrownBy(() -> service.execute(BATCH, with(operation, "r-1", true, null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCodes.MISSING_FIELDS);
                    assertThat(e.details()).containsExactly("requestedBy");
                });
    }

    @Test
    void holdWithoutRequestedByIsAnActionOfTheSystemInTheAudit() {
        service.execute(BATCH, with(PhaseOperation.HOLD, "falla-1", null, null));

        ArgumentCaptor<UUID> user = ArgumentCaptor.forClass(UUID.class);
        verify(audit).record(user.capture(), any(), any(), any(), any(), any());
        assertThat(user.getValue()).isNull();
    }

    @Test
    void theRequestIdAcceptsUpTo64CharactersAndNoMore() {
        service.execute(BATCH, with(PhaseOperation.HOLD, "x".repeat(64), null, null));

        assertThatThrownBy(() -> service.execute(BATCH, with(PhaseOperation.HOLD, "x".repeat(65), null, null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_REQUEST_ID));
    }

    @Test
    void aBatchThatDoesNotExistIsNotFound() {
        batchExists = false;

        assertThatThrownBy(() -> service.execute(BATCH, command(PhaseOperation.DISCARD, "r-1")))
                .isInstanceOfSatisfying(NotFoundException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.BATCH_NOT_FOUND));
        assertThat(log).isEmpty();
    }

    // ---- empresa inactiva (RF-003): solo START_EVALUATION se bloquea ----

    @Test
    void onlyStartEvaluationIsBlockedForAnInactiveCompany() {
        companies.put(COMPANY, CompanyProjection.of(COMPANY, "Hilos SAC", CompanyStatus.INACTIVE, START));

        assertThatThrownBy(() -> service.execute(BATCH, command(PhaseOperation.START_EVALUATION, "c-1")))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.COMPANY_INACTIVE));
        assertThat(log).isEmpty();
        verify(events, never()).publish(any());

        service.execute(BATCH, command(PhaseOperation.HOLD, "f-1"));
        service.execute(BATCH, command(PhaseOperation.RELEASE_HOLD, "f-2"));
        service.execute(BATCH, command(PhaseOperation.DISCARD, "c-2"));
        phase = BatchPhase.IN_QUALITY_CONTROL;
        assertThat(service.execute(BATCH, command(PhaseOperation.RELEASE, "c-3")).phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    void onlyStartEvaluationIsBlockedForAnUnknownCompany() {
        companies.clear();

        assertThatThrownBy(() -> service.execute(BATCH, command(PhaseOperation.START_EVALUATION, "c-1")))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.COMPANY_UNKNOWN));

        assertThat(service.execute(BATCH, command(PhaseOperation.HOLD, "f-1")).qualityHold()).isTrue();
        assertThat(service.execute(BATCH, command(PhaseOperation.DISCARD, "c-2")).phase()).isEqualTo(BatchPhase.DISCARDED);
    }

    // ---- razón y auditoría ----

    @Test
    void theReasonOfTheCommandIsTheReasonOfTheEventAndFallsBackToTheOperation() {
        service.execute(BATCH, new PhaseTransitionCommand(PhaseOperation.HOLD, "f-1", null, "  Posible falla derivada  ", null));
        hold = false;
        service.execute(BATCH, command(PhaseOperation.HOLD, "f-2"));

        assertThat(publishedEvents()).extracting(e -> ((BatchPhaseChanged) e).reason()).containsExactly("Posible falla derivada", "HOLD");
    }

    @Test
    void aSuccessfulCommandWritesOneAuditRowWithWhoAndWhat() {
        service.execute(BATCH, command(PhaseOperation.DISCARD, "c-1"));

        ArgumentCaptor<Map<String, Object>> changes = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(org.mockito.ArgumentMatchers.eq(USER), org.mockito.ArgumentMatchers.eq(COMPANY),
                org.mockito.ArgumentMatchers.eq("BATCH_PHASE_COMMAND"), org.mockito.ArgumentMatchers.eq("Batch"),
                org.mockito.ArgumentMatchers.eq(BATCH.toString()), changes.capture());
        assertThat(changes.getValue()).containsEntry("operation", "DISCARD").containsEntry("requestId", "c-1")
                .containsEntry("fromPhase", "AVAILABLE").containsEntry("toPhase", "DISCARDED");
    }

    // ---- concurrencia: reintento de la transacción completa y 503 ----

    @Test
    void everyAttemptStartsByLimitingTheLockWait() {
        service.execute(BATCH, command(PhaseOperation.DISCARD, "c-1"));

        verify(lockWait).limit(Duration.ofMillis(1500));
    }

    @Test
    void aVersionConflictRetriesTheWholeTransactionAndThenSucceedsWithASingleEvent() {
        int[] failures = {2};
        doAnswer(call -> {
            if (failures[0]-- > 0) {
                throw new ObjectOptimisticLockingFailureException(Batch.class, BATCH);
            }
            return savedAtVersion(call);
        }).when(batches).saveAll(any());

        PhaseTransitionResult result = service.execute(BATCH, command(PhaseOperation.DISCARD, "c-1"));

        assertThat(result.phase()).isEqualTo(BatchPhase.DISCARDED);
        verify(batches, times(3)).findById(BATCH);
        verify(lockWait, times(3)).limit(any());
        assertThat(logSaves).containsExactly("c-1|DISCARD");
    }

    @Test
    void aVersionConflictThatNeverGoesAwayIsA503NotA409() {
        doThrow(new ObjectOptimisticLockingFailureException(Batch.class, BATCH)).when(batches).saveAll(any());

        assertThatThrownBy(() -> service.execute(BATCH, command(PhaseOperation.DISCARD, "c-1")))
                .isInstanceOf(TemporarilyUnavailableException.class).isNotInstanceOf(TrazatexException.class)
                .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(batches, times(3)).findById(BATCH);
        assertThat(log).isEmpty();
    }

    @Test
    void aDuplicateKeyInTheLogMeansAnotherTransactionWonAndTheRetryReturnsItsResult() {
        logSavesToFailWithDuplicate = 1;

        PhaseTransitionResult result = service.execute(BATCH, command(PhaseOperation.HOLD, "f-1"));

        assertThat(result).isEqualTo(new PhaseTransitionResult(BATCH, BatchPhase.IN_QUALITY_CONTROL, false));
        verify(batches, times(2)).findById(BATCH);
    }

    @Test
    void aLockWaitIsNotRetriedInsideBecauseItAlreadySpentItsTimeAndQualityIsAboutToGiveUp() {
        doThrow(new CannotAcquireLockException("canceling statement due to lock timeout")).when(batches).saveAll(any());

        assertThatThrownBy(() -> service.execute(BATCH, command(PhaseOperation.DISCARD, "c-1")))
                .isInstanceOf(TemporarilyUnavailableException.class);
        verify(batches, times(1)).findById(BATCH);
    }

    @Test
    void aBusinessRejectionIsNeverRetried() {
        phase = BatchPhase.DISCARDED;

        assertThatThrownBy(() -> service.execute(BATCH, command(PhaseOperation.DISCARD, "c-1"))).isInstanceOf(ConflictException.class);

        verify(batches, times(1)).findById(BATCH);
    }
}
