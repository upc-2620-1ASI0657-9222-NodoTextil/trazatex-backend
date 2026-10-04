package com.nodotextil.trazatex.traceability.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FailureImpactAnalyzerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 20, 14, 0);

    private final UUID f = UUID.randomUUID();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID y = UUID.randomUUID();
    private final UUID b1 = UUID.randomUUID();
    private final UUID b2 = UUID.randomUUID();

    private final FailureImpactAnalyzer analyzer = new FailureImpactAnalyzer();
    private LineageGraph family;

    @BeforeEach
    void setUp() {
        family = new LineageGraph(b1, Set.of(
                edge(f, a, LineageType.DIVISION),
                edge(f, b, LineageType.DIVISION),
                edge(a, y, LineageType.TRANSFORMATION),
                edge(b, b1, LineageType.DIVISION),
                edge(b, b2, LineageType.DIVISION)));
    }

    @Test
    void returnsRelatedAvailableLotsAndDistinguishesDivisionFromTransformation() {
        Set<UUID> available = Set.of(y, b2);

        List<AffectedLot> affected = analyzer.analyze(b1, family, available::contains);

        assertThat(affected).containsExactlyInAnyOrder(
                new AffectedLot(y, AffectedLot.Reason.TRANSFORMATION_OUTPUT),
                new AffectedLot(b2, AffectedLot.Reason.DIVISION_BRANCH));
    }

    @Test
    void historicalLotsRebuildTheFamilyButAreNotReturned() {
        Set<UUID> available = Set.of(b2);

        List<AffectedLot> affected = analyzer.analyze(b1, family, available::contains);

        assertThat(affected).extracting(AffectedLot::batchId).containsExactly(b2);
    }

    @Test
    void neverReturnsTheFailedLotNorItsAncestors() {
        Set<UUID> everyLotAvailable = Set.of(f, a, b, y, b1, b2);

        List<AffectedLot> affected = analyzer.analyze(b1, family, everyLotAvailable::contains);

        assertThat(affected).extracting(AffectedLot::batchId)
                .containsExactlyInAnyOrder(a, y, b2)
                .doesNotContain(b1, b, f);
    }

    @Test
    void reviewsTheWholeFamilyWhenTheFailureIsInTheMiddleOfTheGenealogy() {
        Set<UUID> available = Set.of(b, y, b1, b2);

        List<AffectedLot> affected = analyzer.analyze(a, family, available::contains);

        assertThat(affected).extracting(AffectedLot::batchId)
                .containsExactlyInAnyOrder(b, y, b1, b2);
    }

    @Test
    void aLotWithoutGenealogyAffectsNoOtherLot() {
        UUID alone = UUID.randomUUID();

        List<AffectedLot> affected = analyzer.analyze(
                alone, LineageGraph.empty(alone), lot -> true);

        assertThat(affected).isEmpty();
    }

    private LineageEdge edge(UUID parent, UUID child, LineageType type) {
        return new LineageEdge(parent, child, type, NOW);
    }
}
