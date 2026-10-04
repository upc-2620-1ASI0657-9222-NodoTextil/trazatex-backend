package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import com.nodotextil.trazatex.traceability.domain.LineageGraph;
import com.nodotextil.trazatex.traceability.support.ConcurrentQueries;
import com.nodotextil.trazatex.traceability.support.LineageGraphGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("performance")
@EnabledIfEnvironmentVariable(named = "NEO4J_PERFORMANCE", matches = "true")
class Neo4jLineagePerformanceTest {

    private static final int CONCURRENT_QUERIES = 100;
    private static final long LIMIT_MILLIS = 5_000;

    private static Driver driver;
    private static Neo4jClient client;
    private static Neo4jLineageRepository repository;
    private static LineageGraphGenerator generator;

    @BeforeAll
    static void loadGenealogy() {
        driver = GraphDatabase.driver(
                env("NEO4J_URI", "bolt://localhost:7687"),
                AuthTokens.basic(env("NEO4J_USER", "neo4j"), env("NEO4J_PASSWORD", "trazatex-pass")));
        client = Neo4jClient.create(driver);
        client.query("CREATE CONSTRAINT lot_batch_id IF NOT EXISTS "
                + "FOR (l:Lot) REQUIRE l.batchId IS UNIQUE").run();
        client.query("MATCH (l:Lot) DETACH DELETE l").run();
        repository = new Neo4jLineageRepository(client);
        generator = new LineageGraphGenerator().populate(repository);
    }

    @AfterAll
    static void cleanUp() {
        client.query("MATCH (l:Lot) DETACH DELETE l").run();
        driver.close();
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

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
