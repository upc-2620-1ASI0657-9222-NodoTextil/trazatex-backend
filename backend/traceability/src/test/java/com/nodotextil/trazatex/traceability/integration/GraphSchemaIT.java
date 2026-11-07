package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.infrastructure.config.GraphSchemaInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Restricciones e índices del grafo (ADR-002, docs/architecture/data.md): existen tras arrancar, se hacen cumplir y se pueden recrear. */
class GraphSchemaIT extends IntegrationTestBase {

    @Autowired
    GraphSchemaInitializer initializer;

    private List<Map<String, Object>> rows(String cypher) {
        return new ArrayList<>(neo4j.query(cypher).fetch().all());
    }

    @Test
    void theUniquenessConstraintsOfTheModelExistAfterStartup() {
        assertThat(rows("SHOW CONSTRAINTS YIELD name, type, labelsOrTypes, properties"))
                .extracting(row -> row.get("name") + "|" + row.get("type") + "|" + row.get("labelsOrTypes") + "|" + row.get("properties"))
                .contains("batch_id_unique|UNIQUENESS|[Batch]|[batchId]",
                        "batch_traceability_id_unique|UNIQUENESS|[Batch]|[traceabilityId]",
                        "transformation_id_unique|UNIQUENESS|[Transformation]|[transformationId]",
                        "company_id_unique|UNIQUENESS|[Company]|[companyId]",
                        "processed_event_id_unique|UNIQUENESS|[ProcessedEvent]|[eventId]",
                        "outbox_event_id_unique|UNIQUENESS|[OutboxEvent]|[eventId]");
    }

    @Test
    void theLookupIndexesExistAndAreOnline() {
        assertThat(rows("SHOW INDEXES YIELD name, type, state, labelsOrTypes, properties WHERE type = 'RANGE'"))
                .filteredOn(row -> row.get("name").equals("batch_qr_code") || row.get("name").equals("outbox_event_pending") || row.get("name").equals("batch_company_traceability")
                        || row.get("name").equals("outbox_event_aggregate"))
                .extracting(row -> row.get("name") + "|" + row.get("state") + "|" + row.get("labelsOrTypes") + "|" + row.get("properties"))
                .containsExactlyInAnyOrder("batch_qr_code|ONLINE|[Batch]|[qrCode]", "outbox_event_pending|ONLINE|[OutboxEvent]|[status, createdAt]",
                        "batch_company_traceability|ONLINE|[Batch]|[companyId, traceabilityId]", "outbox_event_aggregate|ONLINE|[OutboxEvent]|[aggregateId, eventType]");
    }

    @ParameterizedTest
    @CsvSource({"Batch,batchId", "Batch,traceabilityId", "Transformation,transformationId", "Company,companyId",
            "ProcessedEvent,eventId", "OutboxEvent,eventId"})
    void aDuplicateValueIsRejected(String label, String property) {
        neo4j.query("CREATE (:" + label + " {" + property + ": 'x'})").run();

        assertThatThrownBy(() -> neo4j.query("CREATE (:" + label + " {" + property + ": 'x'})").run())
                .hasMessageContaining("already exists");
        assertThat(neo4j.query("MATCH (n:" + label + ") RETURN count(n) AS n").fetch().one().orElseThrow()).containsEntry("n", 1L);
    }

    @Test
    void severalProvisionalBatchesWithoutTraceabilityIdOrQrCodeCanCoexist() {
        neo4j.query("CREATE (:Batch {batchId: 'a', provisional: true}), (:Batch {batchId: 'b', provisional: true})").run();

        assertThat(neo4j.query("MATCH (b:Batch) RETURN count(b) AS n").fetch().one().orElseThrow()).containsEntry("n", 2L);
    }

    @Test
    void concurrentMergesOfTheSameBatchesCreateExactlyOneNodeEach() throws Exception {
        int ids = 40;
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier start = new CyclicBarrier(threads);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                List<Integer> order = new ArrayList<>();
                for (int i = 0; i < ids; i++) {
                    order.add(i);
                }
                Collections.shuffle(order, new java.util.Random(t));
                Callable<Integer> task = () -> {
                    start.await(10, TimeUnit.SECONDS);
                    for (int id : order) {
                        neo4j.query("MERGE (b:Batch {batchId: $id}) ON CREATE SET b.provisional = true").bind("batch-" + id).to("id").run();
                    }
                    return order.size();
                };
                futures.add(pool.submit(task));
            }
            for (Future<Integer> future : futures) {
                assertThat(future.get(60, TimeUnit.SECONDS)).isEqualTo(ids);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(neo4j.query("MATCH (b:Batch) RETURN count(b) AS n, count(DISTINCT b.batchId) AS d").fetch().one().orElseThrow())
                .containsEntry("n", (long) ids).containsEntry("d", (long) ids);
    }

    @Test
    void creatingTheSchemaTwiceIsHarmless() {
        assertThatCode(() -> {
            initializer.ensureSchema();
            initializer.ensureSchema();
        }).doesNotThrowAnyException();
    }

    @Test
    void existingDataThatBreaksAConstraintStopsTheStartupWithAMessageThatNamesIt() {
        neo4j.query("DROP CONSTRAINT batch_id_unique").run();
        try {
            neo4j.query("CREATE (:Batch {batchId: 'dup'}), (:Batch {batchId: 'dup'})").run();

            assertThatThrownBy(initializer::ensureSchema).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("batch_id_unique").hasMessageContaining("NEO4J_URI");
        } finally {
            neo4j.query("MATCH (n) DETACH DELETE n").run();
            initializer.ensureSchema();
        }
        assertThat(rows("SHOW CONSTRAINTS YIELD name")).extracting(row -> row.get("name")).contains("batch_id_unique");
    }
}
