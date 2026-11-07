package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.OTHER_COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.availableBatch;
import static com.nodotextil.trazatex.production.domain.Fixtures.batch;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * La regla de communication.md: todo cambio de fase o de retención por calidad queda anotado, sea cual sea la operación
 * (y solo ese): cada operación contra cada fase y cada valor de la retención, más una comprobación por reflexión de que
 * ninguna operación de {@link Batch} queda fuera de la tabla.
 */
class BatchPhaseChangeTest {

    private enum Operation {
        SPLIT("markSplit", Batch::markSplit),
        START_TRANSFORMATION("startTransformation", Batch::startTransformation),
        FINISH_TRANSFORMATION("finishTransformation", Batch::finishTransformation),
        START_TRANSFER("startTransfer", Batch::startTransfer),
        ACCEPT_TRANSFER("acceptTransfer", batch -> batch.acceptTransfer(OTHER_COMPANY)),
        REJECT_TRANSFER("rejectTransfer", Batch::rejectTransfer),
        START_EVALUATION("startEvaluation", Batch::startEvaluation),
        RELEASE_CONFORMING("release", batch -> batch.release(false)),
        RELEASE_WITH_FAILURE("release", batch -> batch.release(true)),
        DISCARD("discard", Batch::discard),
        HOLD("hold", Batch::hold),
        RELEASE_HOLD("releaseHold", Batch::releaseHold);

        private final String method;
        private final Consumer<Batch> action;

        Operation(String method, Consumer<Batch> action) {
            this.method = method;
            this.action = action;
        }
    }

    static Stream<Arguments> everyOperationInEveryPhaseAndHold() {
        return Stream.of(Operation.values()).flatMap(operation -> Stream.of(BatchPhase.values())
                .flatMap(phase -> Stream.of(false, true).map(hold -> Arguments.of(operation, phase, hold))));
    }

    @ParameterizedTest(name = "{0} sobre {1}, retención={2}")
    @MethodSource("everyOperationInEveryPhaseAndHold")
    void aChangeIsRecordedExactlyWhenThePhaseOrTheHoldChange(Operation operation, BatchPhase phase, boolean hold) {
        Batch batch = batch(phase, hold, false, "10");
        boolean accepted = true;
        try {
            operation.action.accept(batch);
        } catch (ConflictException rejected) {
            accepted = false;
        }

        List<BatchPhaseChange> changes = batch.drainPhaseChanges();
        if (!accepted) {
            assertThat(changes).as("una operación rechazada no anota nada").isEmpty();
            assertThat(batch.phase()).isEqualTo(phase);
            assertThat(batch.qualityHold()).isEqualTo(hold);
        } else if (batch.phase() == phase && batch.qualityHold() == hold) {
            assertThat(changes).as("una operación que no cambia fase ni retención no anota nada").isEmpty();
        } else {
            assertThat(changes).as("un solo cambio por operación").hasSize(1);
            BatchPhaseChange change = changes.get(0);
            assertThat(change.batchId()).isEqualTo(batch.id());
            assertThat(change.previousPhase()).isEqualTo(phase);
            assertThat(change.newPhase()).isEqualTo(batch.phase());
            assertThat(change.qualityHold()).isEqualTo(batch.qualityHold());
            assertThat(change.companyId()).isEqualTo(batch.companyId());
        }
    }

    @Test
    void holdingAnAlreadyHeldBatchAndReleasingAnUnheldOneChangeNothing() {
        Batch held = batch(BatchPhase.AVAILABLE, true, false, "10");
        Batch free = batch(BatchPhase.AVAILABLE, false, false, "10");

        assertThat(held.hold()).isFalse();
        assertThat(free.releaseHold()).isFalse();

        assertThat(held.hasPhaseChanges()).isFalse();
        assertThat(free.hasPhaseChanges()).isFalse();
    }

    @Test
    void aHoldAloneChangesOnlyTheRetentionAndKeepsThePhase() {
        Batch batch = batch(BatchPhase.AVAILABLE, false, false, "10");

        assertThat(batch.hold()).isTrue();

        assertThat(batch.drainPhaseChanges()).containsExactly(
                new BatchPhaseChange(batch.id(), COMPANY, BatchPhase.AVAILABLE, BatchPhase.AVAILABLE, true));
    }

    @Test
    void releaseChangesThePhaseAndTheRetentionInASingleChange() {
        Batch batch = batch(BatchPhase.IN_QUALITY_CONTROL, false, false, "10");

        batch.release(true);

        assertThat(batch.drainPhaseChanges()).containsExactly(
                new BatchPhaseChange(batch.id(), COMPANY, BatchPhase.IN_QUALITY_CONTROL, BatchPhase.AVAILABLE, true));
    }

    @Test
    void acceptingATransferReportsTheCompanyThatNowOwnsTheBatch() {
        Batch batch = batch(BatchPhase.IN_TRANSFER, false, false, "10");

        batch.acceptTransfer(OTHER_COMPANY);

        assertThat(batch.drainPhaseChanges()).containsExactly(
                new BatchPhaseChange(batch.id(), OTHER_COMPANY, BatchPhase.IN_TRANSFER, BatchPhase.AVAILABLE, false));
    }

    @Test
    void severalChangesAreKeptInOrderAndDeliveredOnlyOnce() {
        Batch batch = availableBatch("10");

        batch.startEvaluation();
        batch.release(true);
        batch.releaseHold();

        assertThat(batch.drainPhaseChanges()).extracting(BatchPhaseChange::newPhase, BatchPhaseChange::qualityHold)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(BatchPhase.IN_QUALITY_CONTROL, false),
                        org.assertj.core.groups.Tuple.tuple(BatchPhase.AVAILABLE, true),
                        org.assertj.core.groups.Tuple.tuple(BatchPhase.AVAILABLE, false));
        assertThat(batch.drainPhaseChanges()).as("lo entregado no se vuelve a entregar").isEmpty();
        assertThat(batch.hasPhaseChanges()).isFalse();
    }

    @Test
    void aBatchThatIsBornOrJustReadHasNoChanges() {
        assertThat(availableBatch("10").hasPhaseChanges()).isFalse();
        assertThat(batch(BatchPhase.IN_TRANSFORMATION, true, false, "10").hasPhaseChanges()).isFalse();
    }

    @Test
    void thingsThatAreNotPhaseOrRetentionNeverRecordAChange() {
        Batch finalProduct = batch(BatchPhase.AVAILABLE, false, true, "10");

        finalProduct.updateCommercialData(new CommercialData("Tiendas SAC", null, null, null, null));
        finalProduct.showQualityStatus(QualityStatus.CONFORMING);

        assertThat(finalProduct.hasPhaseChanges()).isFalse();
    }

    // ---- completitud: ninguna operación de Batch queda fuera de la tabla ----

    /** Métodos públicos de Batch que no son transiciones: lecturas, validaciones y datos que no tocan fase ni retención. */
    private static final Set<String> NOT_TRANSITIONS = Set.of("ensureEnabled", "ensurePhase", "updateCommercialData",
            "showQualityStatus", "isEnabled", "isFinalProduct", "qualityHold", "hasPhaseChanges");

    @Test
    void everyPublicOperationOfBatchIsCoveredByTheTable() {
        Set<String> declared = new TreeSet<>();
        for (Method method : Batch.class.getDeclaredMethods()) {
            boolean candidate = Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
                    && (method.getReturnType() == void.class || method.getReturnType() == boolean.class)
                    && !NOT_TRANSITIONS.contains(method.getName());
            if (candidate) {
                declared.add(method.getName());
            }
        }
        Set<String> covered = new TreeSet<>(Arrays.stream(Operation.values()).map(o -> o.method).toList());

        assertThat(declared)
                .as("Batch tiene una operación pública nueva: agréguela a Operation (y a BatchChanges/los casos de uso) o, si no "
                        + "toca fase ni retención, a NOT_TRANSITIONS")
                .isEqualTo(covered);
    }
}
