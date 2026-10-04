package com.nodotextil.trazatex.traceability.performance;

import com.nodotextil.trazatex.traceability.domain.AffectedLot;
import com.nodotextil.trazatex.traceability.domain.FailureImpactAnalyzer;
import com.nodotextil.trazatex.traceability.domain.LineageGraph;
import com.nodotextil.trazatex.traceability.support.ConcurrentQueries;
import com.nodotextil.trazatex.traceability.support.InMemoryLineageRepository;
import com.nodotextil.trazatex.traceability.support.LineageGraphGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("performance")
class LineagePerformanceTest {

    private static final int CONCURRENT_QUERIES = 100;
    private static final long LIMIT_MILLIS = 5_000;

    private InMemoryLineageRepository repository;
    private LineageGraphGenerator generator;

    @BeforeEach
    void buildGenealogy() {
        repository = new InMemoryLineageRepository();
        generator = new LineageGraphGenerator().populate(repository);
    }

    @Test
    void theGenealogyHasFiveHundredNodes() {
        assertThat(generator.totalNodes()).isEqualTo(500);
        assertThat(repository.edges()).isNotEmpty();
    }

    @Test
    void ninetyFivePercentOfConcurrentBackwardQueriesRespondWithinFiveSeconds() throws Exception {
        List<Long> millis = ConcurrentQueries.run(CONCURRENT_QUERIES, index -> {
            LineageGraph graph = repository.findAncestors(generator.deepestBatch(index));
            assertThat(graph.edges()).isNotEmpty();
        });

        assertThat(ConcurrentQueries.percentile95(millis)).isLessThanOrEqualTo(LIMIT_MILLIS);
    }

    @Test
    void ninetyFivePercentOfConcurrentForwardQueriesRespondWithinFiveSeconds() throws Exception {
        List<Long> millis = ConcurrentQueries.run(CONCURRENT_QUERIES, index -> {
            LineageGraph graph = repository.findDescendants(generator.rootBatch(index));
            assertThat(graph.edges()).isNotEmpty();
        });

        assertThat(ConcurrentQueries.percentile95(millis)).isLessThanOrEqualTo(LIMIT_MILLIS);
    }

    @Test
    void ninetyFivePercentOfConcurrentFailureAnalysesRespondWithinFiveSeconds() throws Exception {
        FailureImpactAnalyzer analyzer = new FailureImpactAnalyzer();

        List<Long> millis = ConcurrentQueries.run(CONCURRENT_QUERIES, index -> {
            UUID failed = generator.deepestBatch(index);
            LineageGraph family = repository.findFamily(failed);
            List<AffectedLot> affected = analyzer.analyze(failed, family, lot -> true);
            assertThat(affected).extracting(AffectedLot::batchId).doesNotContain(failed);
        });

        assertThat(ConcurrentQueries.percentile95(millis)).isLessThanOrEqualTo(LIMIT_MILLIS);
    }
}
