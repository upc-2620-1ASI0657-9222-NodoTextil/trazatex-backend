package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.dto.OutputBatch;
import com.nodotextil.trazatex.quality.application.dto.SplitChild;
import com.nodotextil.trazatex.quality.application.port.in.SyncBatchProjectionUseCase;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.nodotextil.trazatex.quality.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.quality.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.quality.domain.Fixtures.OTHER_COMPANY;
import static com.nodotextil.trazatex.quality.domain.Fixtures.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * La proyección no depende del orden en que lleguen los eventos de los distintos tópicos: cualquier permutación de los eventos de un
 * lote deja el mismo estado.
 */
class BatchProjectionServiceTest {

    private final InMemoryBatchProjections rows = new InMemoryBatchProjections();
    private final com.nodotextil.trazatex.quality.application.port.out.ImpactedBatchRepositoryPort impacted =
            org.mockito.Mockito.mock(com.nodotextil.trazatex.quality.application.port.out.ImpactedBatchRepositoryPort.class);
    private final SyncBatchProjectionUseCase projection = new BatchProjectionService(rows, impacted, Clock.fixed(NOW, ZoneOffset.UTC));

    private static <T> List<List<T>> permutations(List<T> items) {
        if (items.size() <= 1) {
            return List.of(items);
        }
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            List<T> rest = new ArrayList<>(items);
            T head = rest.remove(i);
            for (List<T> tail : permutations(rest)) {
                List<T> permutation = new ArrayList<>();
                permutation.add(head);
                permutation.addAll(tail);
                result.add(permutation);
            }
        }
        return result;
    }

    private BatchProjection row(UUID batchId) {
        return rows.findById(batchId).orElseThrow();
    }

    @Test
    void aRegisteredBatchIsKnownAsAvailableAndNotReviewed() {
        UUID batch = id();

        projection.registered(batch, COMPANY);

        assertThat(row(batch).phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(row(batch).qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
        assertThat(row(batch).companyId()).isEqualTo(COMPANY);
    }

    @Test
    void registeringTwiceKeepsOneRowAndNeverOverwritesTheVersionOrThePhase() {
        UUID batch = id();
        projection.registered(batch, COMPANY);
        projection.phaseChanged(batch, COMPANY, BatchPhase.IN_TRANSFORMATION, false, 3);

        projection.registered(batch, COMPANY);

        assertThat(rows.size()).isEqualTo(1);
        assertThat(row(batch).phase()).isEqualTo(BatchPhase.IN_TRANSFORMATION);
        assertThat(row(batch).batchVersion()).isEqualTo(3);
    }

    @Test
    void registeredAndTwoPhaseChangesGiveTheSameStateInEveryOrder() {
        List<Consumer<UUID>> events = List.of(
                batch -> projection.registered(batch, COMPANY),
                batch -> projection.phaseChanged(batch, COMPANY, BatchPhase.IN_QUALITY_CONTROL, false, 1),
                batch -> projection.phaseChanged(batch, OTHER_COMPANY, BatchPhase.AVAILABLE, true, 2));

        for (List<Consumer<UUID>> order : permutations(events)) {
            UUID batch = id();
            order.forEach(event -> event.accept(batch));

            BatchProjection result = row(batch);
            assertThat(result.phase()).isEqualTo(BatchPhase.AVAILABLE);
            assertThat(result.qualityHold()).isTrue();
            assertThat(result.companyId()).isEqualTo(OTHER_COMPANY);
            assertThat(result.batchVersion()).isEqualTo(2);
            assertThat(result.qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
        }
    }

    @Test
    void aSplitChildWhoseFirstNewsIsAPhaseChangeEndsUpWithTheInheritedStatusInEveryOrder() {
        List<Consumer<UUID>> events = List.of(
                child -> projection.split(COMPANY, List.of(new SplitChild(child, QualityStatus.CONFORMING))),
                child -> projection.phaseChanged(child, COMPANY, BatchPhase.IN_TRANSFER, false, 1),
                child -> projection.phaseChanged(child, OTHER_COMPANY, BatchPhase.AVAILABLE, false, 2));

        for (List<Consumer<UUID>> order : permutations(events)) {
            UUID child = id();
            order.forEach(event -> event.accept(child));

            BatchProjection result = row(child);
            assertThat(result.qualityStatus()).isEqualTo(QualityStatus.CONFORMING);
            assertThat(result.everControlled()).isTrue();
            assertThat(result.phase()).isEqualTo(BatchPhase.AVAILABLE);
            assertThat(result.companyId()).isEqualTo(OTHER_COMPANY);
        }
    }

    @Test
    void aSplitCreatesEveryChildOnce() {
        UUID one = id();
        UUID two = id();

        projection.split(COMPANY, List.of(new SplitChild(one, QualityStatus.NOT_REVIEWED), new SplitChild(two, QualityStatus.NOT_REVIEWED)));
        projection.split(COMPANY, List.of(new SplitChild(one, QualityStatus.NOT_REVIEWED), new SplitChild(two, QualityStatus.NOT_REVIEWED)));

        assertThat(rows.size()).isEqualTo(2);
        assertThat(row(one).everControlled()).isFalse();
    }

    @Test
    void transformationOutputsAreBornAvailableAndNotReviewedInEveryOrderWithTheirPhaseChange() {
        List<Consumer<UUID>> events = List.of(
                output -> projection.transformationOutputs(COMPANY, List.of(new OutputBatch(output, "TT-OUTPUT00001"))),
                output -> projection.phaseChanged(output, COMPANY, BatchPhase.IN_TRANSFER, false, 1));

        for (List<Consumer<UUID>> order : permutations(events)) {
            UUID output = id();
            order.forEach(event -> event.accept(output));

            assertThat(row(output).qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
            assertThat(row(output).phase()).isEqualTo(BatchPhase.IN_TRANSFER);
            assertThat(row(output).traceabilityId()).isEqualTo("TT-OUTPUT00001");
        }
    }

    @Test
    void theTraceabilityIdComesFromTheBirthEventsAndIsKeptInEveryOrder() {
        UUID registered = id();
        UUID child = id();
        projection.phaseChanged(registered, COMPANY, BatchPhase.AVAILABLE, false, 1);   // la fila nace parcial, sin Traceability ID
        assertThat(row(registered).traceabilityId()).isNull();

        projection.registered(registered, COMPANY, "TT-REGISTERED1");
        projection.split(COMPANY, List.of(new SplitChild(child, QualityStatus.NOT_REVIEWED, "TT-CHILD000001")));
        projection.registered(registered, COMPANY, "TT-OTHERVALUE1");   // un evento repetido no lo pisa

        assertThat(row(registered).traceabilityId()).isEqualTo("TT-REGISTERED1");
        assertThat(row(child).traceabilityId()).isEqualTo("TT-CHILD000001");
    }

    @Test
    void aRowFromBeforeV4WithoutItsTraceabilityIdGetsItWhenTheBirthEventIsProcessedAgain() {
        UUID batch = id();
        rows.insert(BatchProjection.restore(batch, COMPANY, BatchPhase.AVAILABLE, false, QualityStatus.NOT_REVIEWED, false, 1, NOW));

        projection.registered(batch, COMPANY, "TT-LATE0000001");

        assertThat(row(batch).traceabilityId()).isEqualTo("TT-LATE0000001");
        assertThat(row(batch).qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
        assertThat(row(batch).batchVersion()).isEqualTo(1);
    }

    @Test
    void anAcceptedTransferMovesTheBatchesToTheReceivingCompany() {
        UUID one = id();
        UUID two = id();
        projection.registered(one, COMPANY);
        projection.registered(two, COMPANY);

        projection.transferAccepted(List.of(one, two), COMPANY, OTHER_COMPANY);

        assertThat(row(one).companyId()).isEqualTo(OTHER_COMPANY);
        assertThat(row(two).companyId()).isEqualTo(OTHER_COMPANY);
    }

    @Test
    void anAcceptedTransferForAnUnknownBatchOrOneAlreadyMovedOnChangesNothing() {
        UUID unknown = id();
        UUID movedOn = id();
        UUID third = UUID.randomUUID();
        projection.registered(movedOn, third);

        projection.transferAccepted(List.of(unknown, movedOn), COMPANY, OTHER_COMPANY);

        assertThat(rows.findById(unknown)).isEmpty();
        assertThat(row(movedOn).companyId()).isEqualTo(third);
    }

    @Test
    void theTransferEventAndItsPhaseChangeInEitherOrderLeaveTheBatchWithTheReceivingCompany() {
        List<Consumer<UUID>> events = List.of(
                batch -> projection.transferAccepted(List.of(batch), COMPANY, OTHER_COMPANY),
                batch -> projection.phaseChanged(batch, OTHER_COMPANY, BatchPhase.AVAILABLE, false, 2));

        for (List<Consumer<UUID>> order : permutations(events)) {
            UUID batch = id();
            projection.registered(batch, COMPANY);
            projection.phaseChanged(batch, COMPANY, BatchPhase.IN_TRANSFER, false, 1);

            order.forEach(event -> event.accept(batch));

            assertThat(row(batch).companyId()).isEqualTo(OTHER_COMPANY);
            assertThat(row(batch).phase()).isEqualTo(BatchPhase.AVAILABLE);
        }
    }
}
