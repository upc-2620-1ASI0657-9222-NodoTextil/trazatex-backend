package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyRegisteredUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.QueryLineageUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.integration.GraphBuilder.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cuántas consultas hace a Neo4j una petición de genealogía, para que un N+1 (una consulta por nodo en vez de dos por nivel) se note sin depender del
 * tiempo: la cantidad depende de los <b>niveles</b> recorridos —2 por nivel (por división y por transformación) y un máximo de 10 niveles más una sonda— y de
 * un número fijo (el lote de partida, los nombres, la privacidad ajena y el historial), <b>nunca de la cantidad de nodos ni de empresas</b>. Se cuenta cada
 * llamada a {@code Neo4jClient.query(...)} de la petición (cada una es una consulta).
 *
 * <p>Desglose del tope (por dirección: 2 por nivel × (10 niveles + 1 sonda de lo que hay más allá) = 22) más lo fijo: {@code findBatch} (1),
 * {@code businessNames} (1), {@code privacyOf} (1, solo si hay empresas ajenas) y, solo en la vista completa, {@code transfersOf} (1).
 */
@Import(QueryCounting.Config.class)
class LineageQueryCountIT extends IntegrationTestBase {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    static final int QUERIES_PER_LEVEL = 2;
    static final int LEVELS_PLUS_PROBE = 10 + 1;
    static final int FIXED_ONE_DIRECTION = 3;
    static final int FIXED_BOTH_DIRECTIONS = 4;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    QueryLineageUseCase useCase;
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

    GraphBuilder graph;

    @BeforeEach
    void builder() {
        graph = new GraphBuilder(register, split, transformation, phase, transfer, company, privacy);
        graph.company(A, "Andina SAC").company(B, "Bahía SAC");
    }

    private static void reset() {
        QueryCounting.reset();
    }

    /** Las consultas que hace una petición HTTP de genealogía (la ruta es {@code ancestors}, {@code descendants} o {@code lineage}), como la llama el Gateway. */
    private long queriesOf(String route, String batch, UUID requester) throws Exception {
        return queriesOf(route, id(batch), requester);
    }

    private long queriesOf(String route, UUID batch, UUID requester) throws Exception {
        reset();
        mockMvc.perform(get("/api/traceability/batches/" + batch + "/" + route).header("X-User-Id", "00000000-0000-0000-0000-00000000a0a1")
                .header("X-Company-Id", requester.toString()).header("X-User-Role", "OPERATOR")).andExpect(status().isOk());
        return QueryCounting.count();
    }

    private static long cap(String route) {
        return route.equals("lineage") ? 2L * QUERIES_PER_LEVEL * LEVELS_PLUS_PROBE + FIXED_BOTH_DIRECTIONS : (long) QUERIES_PER_LEVEL * LEVELS_PLUS_PROBE + FIXED_ONE_DIRECTION;
    }

    // ---- no depende de cuántos nodos ----

    @Test
    void theNumberOfQueriesDependsOnTheLevelsAndNotOnTheNumberOfNodes() throws Exception {
        // ancho: una raíz con 60 hijos y cada hijo con 3 hijos (241 nodos, 2 niveles); estrecho: una cadena de 2 niveles (3 nodos). Ambos de B, quien consulta es A
        graph.batch("WIDE", B);
        List<String> children = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            children.add("WC" + i);
        }
        graph.split("WIDE", B, children.toArray(String[]::new));
        for (String child : children) {
            graph.split(child, B, child + "a", child + "b", child + "c");
        }
        graph.chain("NARROW", B, 3);

        for (String route : List.of("descendants", "lineage")) {
            long wide = queriesOf(route, "WIDE", A);
            long narrow = queriesOf(route, "NARROW0", A);

            assertThat(wide).as("%s: 241 nodos contra 3 nodos, mismos 2 niveles", route).isEqualTo(narrow);
        }
    }

    @Test
    void aMixOfManyInputsIsStillOneLevelAndNeitherManyBatchesNorManyCompaniesAddQueries() throws Exception {
        // 40 entradas de 6 empresas distintas se mezclan en una salida (1 nivel hacia atrás); contra una mezcla de 2 entradas de una sola empresa
        UUID[] companies = new UUID[6];
        List<String> inputs = new ArrayList<>();
        for (int c = 0; c < companies.length; c++) {
            companies[c] = UUID.fromString("00000000-0000-0000-0000-00000000f0" + String.format("%02d", c));
            graph.company(companies[c], "Empresa " + c);
        }
        for (int i = 0; i < 40; i++) {
            graph.batch("MI" + i, companies[i % companies.length]);
            inputs.add("MI" + i);
        }
        graph.transform("MT", A, inputs, "MOUT");
        graph.batches(B, "NI1", "NI2").transform("NT", B, List.of("NI1", "NI2"), "NOUT");

        long many = queriesOf("ancestors", "MOUT", A);
        long few = queriesOf("ancestors", "NOUT", A);

        assertThat(many).as("40 entradas de 6 empresas contra 2 de una: mismas consultas (la privacidad ajena es una sola consulta)").isEqualTo(few);
    }

    // ---- 2 por nivel, con un tope ----

    @Test
    void eachExtraLevelCostsAtMostTwoQueriesAndTheCountStopsGrowingAtTheTenLevelLimit() throws Exception {
        graph.chain("c", B, 16); // c0 → c1 → … → c15
        List<Long> byDepth = new ArrayList<>();
        for (int depth = 0; depth <= 15; depth++) {
            byDepth.add(queriesOf("ancestors", "c" + depth, A));
        }

        for (int depth = 1; depth <= 15; depth++) {
            long extra = byDepth.get(depth) - byDepth.get(depth - 1);
            if (depth <= 10) {
                assertThat(extra).as("el nivel %d cuesta como mucho 2 consultas (por división y por transformación)", depth).isLessThanOrEqualTo(QUERIES_PER_LEVEL);
            } else {
                assertThat(extra).as("pasado el límite de 10 niveles no se recorre más: nivel %d", depth).isZero();
            }
            assertThat(byDepth.get(depth)).as("profundidad %d", depth).isLessThanOrEqualTo(cap("ancestors"));
        }
        assertThat(byDepth.get(10)).isEqualTo(byDepth.get(15));
    }

    // ---- el grafo de RNF-04 ----

    @Test
    void aGenealogyOfTheFiveHundredNodeDiamondStaysWithinTheCapInEveryDirection() throws Exception {
        DiamondGraph.load(neo4j, A, B);
        assertThat(DiamondGraph.nodeCount(neo4j)).isEqualTo(500L);

        long ancestors = queriesOf("ancestors", DiamondGraph.batchId(10, 14), A);
        long descendants = queriesOf("descendants", DiamondGraph.batchId(0, 14), A);
        long both = queriesOf("lineage", DiamondGraph.batchId(5, 14), A);

        System.out.printf("CONSULTAS por petición en el diamante de 500 nodos: ancestros=%d descendientes=%d completa=%d (topes: %d / %d / %d)%n", ancestors, descendants, both,
                cap("ancestors"), cap("descendants"), cap("lineage"));
        assertThat(ancestors).as("175 nodos en la respuesta").isLessThanOrEqualTo(cap("ancestors"));
        assertThat(descendants).as("203 nodos en la respuesta").isLessThanOrEqualTo(cap("descendants"));
        assertThat(both).as("122 nodos en la respuesta").isLessThanOrEqualTo(cap("lineage"));
    }

    @Test
    void theWebLayerAddsNoQueriesOfItsOwn() throws Exception {
        graph.chain("w", B, 4);

        long http = queriesOf("ancestors", "w3", A);
        reset();
        useCase.ancestors(id("w3"), A);

        assertThat(http).as("por HTTP las mismas consultas que por el caso de uso").isEqualTo(QueryCounting.count());
    }
}
