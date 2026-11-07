package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyRegisteredUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.QueryLineageUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleLineage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static com.nodotextil.trazatex.traceability.integration.GraphBuilder.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cuánto tarda UNA consulta de genealogía, sin contar la carga del grafo (que se arma antes): varias repeticiones tras calentar, con la mediana y el
 * máximo. Es la base de RNF-04 (en 05c-4 serán 100 a la vez). La cota de la prueba es holgada a propósito (no es la prueba de RNF-04): lo que importa
 * es la cifra que imprime.
 */
class LineageQueryTimingIT extends IntegrationTestBase {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final int WARMUP = 10;
    static final int REPETITIONS = 40;

    @Autowired
    QueryLineageUseCase queries;
    @Autowired
    RegisterBatchUseCase register;
    @Autowired
    ApplyBatchSplitUseCase split;
    @Autowired
    ApplyTransformationCompletedUseCase transformation;
    @Autowired
    ApplyBatchPhaseChangedUseCase phase;
    @Autowired
    ApplyTransferAcceptedUseCase transfer;
    @Autowired
    ApplyCompanyRegisteredUseCase company;
    @Autowired
    ApplyCompanyPrivacyUseCase privacy;

    private static void report(String name, VisibleLineage sample, Supplier<VisibleLineage> query) {
        for (int i = 0; i < WARMUP; i++) {
            query.get();
        }
        List<Long> micros = new ArrayList<>();
        for (int i = 0; i < REPETITIONS; i++) {
            long start = System.nanoTime();
            query.get();
            micros.add((System.nanoTime() - start) / 1000);
        }
        Collections.sort(micros);
        long median = micros.get(micros.size() / 2);
        long max = micros.get(micros.size() - 1);
        System.out.printf("TIEMPO %-44s nodos=%3d aristas=%3d  mediana=%5.1f ms  p95=%5.1f ms  máximo=%5.1f ms (%d repeticiones)%n", name, sample.nodes().size(),
                sample.edges().size(), median / 1000.0, micros.get((int) (micros.size() * 0.95)) / 1000.0, max / 1000.0, REPETITIONS);
        assertThat(median).as("%s: mediana en µs", name).isLessThan(2_000_000L);
    }

    @Test
    void timeOneQueryOnTheGraphWithMoreThanAThousandCrossingPaths() {
        GraphBuilder graph = new GraphBuilder(register, split, transformation, phase, transfer, company, privacy);
        graph.batches(A, "a0", "b0");
        for (int layer = 1; layer <= 10; layer++) {
            graph.split("a" + (layer - 1), A, "a" + layer, "b" + layer).split("b" + (layer - 1), A, "a" + layer, "b" + layer);
        }

        report("2^10 caminos, hacia atrás", queries.ancestors(id("a10"), A), () -> queries.ancestors(id("a10"), A));
        report("2^10 caminos, hacia adelante", queries.descendants(id("a0"), A), () -> queries.descendants(id("a0"), A));
        report("2^10 caminos, vista completa", queries.lineage(id("a5"), A), () -> queries.lineage(id("a5"), A));
    }

    @Test
    void timeOneQueryOnAGraphOfAboutFiveHundredNodesWithMixesAndDivisions() {
        DiamondGraph.load(neo4j, A, UUID.fromString("00000000-0000-0000-0000-0000000000b2"));
        assertThat(DiamondGraph.nodeCount(neo4j)).isEqualTo(500L);

        report("≈500 nodos, hacia atrás (capa 10 → 0)", queries.ancestors(DiamondGraph.batchId(10, 14), A), () -> queries.ancestors(DiamondGraph.batchId(10, 14), A));
        report("≈500 nodos, hacia adelante (capa 0 → 10)", queries.descendants(DiamondGraph.batchId(0, 14), A), () -> queries.descendants(DiamondGraph.batchId(0, 14), A));
        report("≈500 nodos, vista completa (capa 5)", queries.lineage(DiamondGraph.batchId(5, 14), A), () -> queries.lineage(DiamondGraph.batchId(5, 14), A));
    }
}
