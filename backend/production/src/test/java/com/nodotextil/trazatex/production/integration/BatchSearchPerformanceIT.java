package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.application.port.out.BatchSearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RNF-03: la búsqueda de lotes no puede recorrer la tabla. Con volumen real (60 000 lotes de dos empresas) se pide a la base
 * el plan de ejecución —{@code EXPLAIN}— de la <b>consulta exacta que genera Hibernate</b> (capturada con un
 * {@code StatementInspector}) y se comprueba que usa los índices previstos. Sin tiempos: un plan es determinista, un
 * cronómetro no.
 */
class BatchSearchPerformanceIT extends ApiTestBase {

    private static final int PER_COMPANY = 30_000;

    @BeforeEach
    void loadVolume() {
        bulkInsertBatches(COMPANY_A, PER_COMPANY, 1);
        bulkInsertBatches(COMPANY_B, PER_COMPANY, 100_000);
        jdbc.sql("ANALYZE batches").update();
    }

    /** Ejecuta la búsqueda, toma el SELECT de lotes que envió Hibernate (no el conteo) y lo explica con los mismos parámetros. */
    private String explainTheListQuery(BatchSearchCriteria criteria, Object... parameters) {
        CapturingStatementInspector.reset();
        inTx(() -> batches.search(criteria));
        String sql = CapturingStatementInspector.captured().stream()
                .filter(s -> s.toLowerCase().contains("from batches") && !s.toLowerCase().contains("count("))
                .reduce((first, second) -> second).orElseThrow(() -> new AssertionError("Hibernate no envió el SELECT de lotes"));
        List<Object> params = new ArrayList<>(List.of(parameters));
        return plan(sql, params);
    }

    private String explainTheCountQuery(BatchSearchCriteria criteria, Object... parameters) {
        CapturingStatementInspector.reset();
        inTx(() -> batches.search(criteria));
        String sql = CapturingStatementInspector.captured().stream()
                .filter(s -> s.toLowerCase().contains("count("))
                .reduce((first, second) -> second).orElseThrow(() -> new AssertionError("Hibernate no envió el conteo"));
        return plan(sql, new ArrayList<>(List.of(parameters)));
    }

    private String plan(String sql, List<Object> parameters) {
        var statement = jdbc.sql("EXPLAIN " + sql);
        for (Object parameter : parameters) {
            statement = statement.param(parameter);
        }
        String plan = String.join("\n", statement.query(String.class).list());
        return plan;
    }

    // ---- (1) prefijo del Traceability ID ----

    @Test
    void thePrefixIndexExistsWithTextPatternOps() {
        String definition = jdbc.sql("SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_batches_company_traceability_prefix'")
                .query(String.class).single();

        assertThat(definition).contains("(company_id, traceability_id text_pattern_ops)");
        assertThat(jdbc.sql("SELECT version FROM flyway_schema_history WHERE description LIKE '%prefix_index%'").query(String.class).single())
                .isEqualTo("2");
    }

    @Test
    void withoutTextPatternOpsThePrefixSearchCannotUseAnyTracebilityIndex() {
        // control negativo: con el operador por defecto (el del UNIQUE de V1) LIKE 'prefijo%' no sirve si la base no usa la
        // colación "C" — por eso V2 existe. Se borra V2 un momento y se restituye siempre (la base se comparte entre pruebas).
        String collation = jdbc.sql("SELECT datcollate FROM pg_database WHERE datname = current_database()").query(String.class).single();
        String definition = jdbc.sql("SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_batches_company_traceability_prefix'")
                .query(String.class).single();
        jdbc.sql("DROP INDEX ix_batches_company_traceability_prefix").update();
        try {
            jdbc.sql("ANALYZE batches").update();
            String plan = explainTheListQuery(new BatchSearchCriteria(COMPANY_A, "TT-000001234", null, null, null, null, null, 0, 20),
                    COMPANY_A, "TT-000001234%", 0, 20);
            if (!collation.equals("C") && !collation.equals("POSIX")) {
                assertThat(plan).as("sin text_pattern_ops el prefijo no usa el UNIQUE de traceability_id (colación %s)", collation)
                        .doesNotContain("ux_batches_traceability_id");
            }
        } finally {
            jdbc.sql(definition).update();
            jdbc.sql("ANALYZE batches").update();
        }
        assertThat(count("SELECT count(*) FROM pg_indexes WHERE indexname = 'ix_batches_company_traceability_prefix'")).isEqualTo(1);
    }

    @Test
    void thePrefixSearchUsesTheTextPatternOpsIndexInsteadOfScanningTheTable() {
        BatchSearchCriteria criteria = new BatchSearchCriteria(COMPANY_A, "TT-000001234", null, null, null, null, null, 0, 20);

        String plan = explainTheListQuery(criteria, COMPANY_A, "TT-000001234%", 0, 20);

        assertThat(plan).contains("ix_batches_company_traceability_prefix");
        assertThat(plan).doesNotContain("Seq Scan on batches");
        assertThat(plan).as("el patrón se pasa como prefijo, no como 'contiene'").contains("Index Cond");
    }

    @Test
    void theCountOfAPrefixSearchUsesTheIndexToo() {
        // 111 coincidencias con página de 5: la página se llena y Spring Data necesita el conteo
        BatchSearchCriteria criteria = new BatchSearchCriteria(COMPANY_A, "TT-00000123", null, null, null, null, null, 0, 5);

        String plan = explainTheCountQuery(criteria, COMPANY_A, "TT-00000123%");

        assertThat(plan).contains("ix_batches_company_traceability_prefix").doesNotContain("Seq Scan on batches");
    }

    @Test
    void theIndexAlsoServesTheLongestPrefixes() {
        for (String prefix : List.of("TT-0000012", "TT-00000123", "TT-0000012345", "TT-0000029999")) {
            String plan = explainTheListQuery(new BatchSearchCriteria(COMPANY_A, prefix, null, null, null, null, null, 0, 20),
                    COMPANY_A, prefix + "%", 0, 20);
            assertThat(plan).as("prefijo %s", prefix).doesNotContain("Seq Scan on batches");
        }
    }

    @Test
    void thePrefixSearchThroughTheApiFindsTheRightBatchesAmongSixtyThousand() throws Exception {
        JsonNode result = read(getAs(OPERATOR_OF_A, "/api/batches?traceabilityId=tt-000001234&size=50").andExpect(status().isOk()));

        List<String> ids = new ArrayList<>();
        result.path("items").forEach(item -> ids.add(item.path("traceabilityId").asString()));
        assertThat(ids).hasSize(10).allMatch(id -> id.startsWith("TT-000001234"));
        assertThat(result.path("totalElements").asLong()).isEqualTo(10);
        assertThat(read(getAs(OPERATOR_OF_B, "/api/batches?traceabilityId=TT-000001234")).path("items")).as("otra empresa: nada").isEmpty();
        assertThat(read(getAs(OPERATOR_OF_B, "/api/batches?traceabilityId=TT-000010123&size=100")).path("items")).hasSize(10);
    }

    // ---- (2) listado paginado con orden estable ----

    @Test
    void theUnfilteredListingReadsInOrderFromTheIndexWithoutSortingTheCompany() {
        BatchSearchCriteria criteria = new BatchSearchCriteria(COMPANY_A, null, null, null, null, null, null, 0, 20);

        String plan = explainTheListQuery(criteria, COMPANY_A, 0, 20);

        assertThat(plan).as("created_at DESC, id sale ordenado del índice de la empresa").contains("ix_batches_company_created");
        assertThat(plan).doesNotContain("Seq Scan on batches").doesNotContainIgnoringCase("Sort");
        assertThat(plan).contains("Limit");
    }

    @Test
    void aDeepPageStillReadsInOrderFromTheIndex() {
        BatchSearchCriteria criteria = new BatchSearchCriteria(COMPANY_A, null, null, null, null, null, null, 50, 100);

        String plan = explainTheListQuery(criteria, COMPANY_A, 5000, 100);

        assertThat(plan).contains("ix_batches_company_created").doesNotContain("Seq Scan on batches");
    }

    @Test
    void filteringByPhaseUsesTheCompanyPhaseIndexOrTheCompanyOrderIndexNeverTheWholeTable() {
        BatchSearchCriteria criteria = new BatchSearchCriteria(COMPANY_A, null, null,
                com.nodotextil.trazatex.production.domain.model.BatchPhase.AVAILABLE, null, null, null, 0, 20);

        String plan = explainTheListQuery(criteria, COMPANY_A, "AVAILABLE", 0, 20);

        assertThat(plan).doesNotContain("Seq Scan on batches");
        assertThat(plan).containsAnyOf("ix_batches_company_stage", "ix_batches_company_created");
    }

    @Test
    void theCompanyIsAlwaysPartOfTheQuery() {
        String sql = captureSql(new BatchSearchCriteria(COMPANY_A, "TT-0", "Fibra", null, null, null, null, 0, 20));
        assertThat(sql.toLowerCase()).contains("company_id=?");
        assertThat(captureSql(new BatchSearchCriteria(UUID.randomUUID(), null, null, null, null, null, null, 0, 20)).toLowerCase())
                .contains("company_id=?");
    }

    private String captureSql(BatchSearchCriteria criteria) {
        CapturingStatementInspector.reset();
        inTx(() -> batches.search(criteria));
        return CapturingStatementInspector.captured().stream().filter(s -> s.toLowerCase().contains("from batches"))
                .findFirst().orElseThrow();
    }
}
