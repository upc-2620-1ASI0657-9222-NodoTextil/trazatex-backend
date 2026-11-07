package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.sharedtesting.TrazatexNeo4jContainer;
import com.nodotextil.trazatex.traceability.infrastructure.persistence.GraphSchema;
import com.nodotextil.trazatex.traceability.infrastructure.persistence.LineageQueries;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.summary.Plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las consultas de lectura usan índices o restricciones, nunca recorren todos los lotes: con {@code EXPLAIN} sobre un grafo con datos no hay
 * ningún {@code AllNodesScan}, {@code NodeByLabelScan} ni {@code NodeIndexScan} (recorrer entero un índice también es leer todos los lotes) y
 * cada punto de entrada es una búsqueda por índice: el QR, el Traceability ID, el prefijo dentro de una empresa y, en cada nivel, el lote de la
 * frontera.
 *
 * <p>El plan que elige Neo4j <b>depende de sus estadísticas</b>, que se muestrean con retraso: justo después de cargar datos están sin muestrear
 * (sin los {@code USING INDEX SEEK} de {@link LineageQueries} el planificador recorría los índices enteros) y más tarde pueden ser favorables por
 * casualidad. Por eso esta prueba tiene su propio Neo4j recién creado (nunca reutiliza uno con estadísticas calentadas por otras pruebas) y comprueba
 * los planes en las dos situaciones: con las estadísticas sin muestrear y después de remuestrearlas.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LineageQueryPlansIT {

    private static final String PASSWORD = "plan-de-consultas";
    static final TrazatexNeo4jContainer NEO4J = new TrazatexNeo4jContainer(PASSWORD);
    static Driver driver;

    private record Operator(String type, String details) {
    }

    @BeforeAll
    static void startAndPopulate() {
        NEO4J.start();
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.basic("neo4j", PASSWORD));
        try (Session session = driver.session()) {
            GraphSchema.CONSTRAINTS.forEach(statement -> session.run(statement.cypher()).consume());
            GraphSchema.INDEXES.forEach(statement -> session.run(statement.cypher()).consume());
            session.run("CALL db.awaitIndexes(60)").consume();
            // ≈ 500 nodos con divisiones y mezclas (capas de lotes unidas por transformaciones), de dos empresas
            session.run("""
                    UNWIND range(0, 10) AS layer
                    UNWIND range(0, 29) AS i
                    CREATE (:Batch {batchId: 'b-' + layer + '-' + i, traceabilityId: 'TT-' + layer + '-' + i, qrCode: 'QR-' + layer + '-' + i,
                      companyId: CASE WHEN i % 2 = 0 THEN 'c1' ELSE 'c2' END, materialType: 'Algodón', phase: 'AVAILABLE', qualityStatus: 'NOT_REVIEWED',
                      finalProduct: false, createdAt: datetime('2026-10-21T10:00:00Z'), provisional: false})""").consume();
            session.run("""
                    UNWIND range(0, 9) AS layer
                    UNWIND range(0, 16) AS i
                    CREATE (t:Transformation {transformationId: 't-' + layer + '-' + i, companyId: 'c1', type: 'DYEING', startedAt: datetime('2026-10-21T10:00:00Z'),
                      completedAt: datetime('2026-10-21T11:00:00Z'), provisional: false})
                    WITH t, layer, i
                    MATCH (a:Batch {batchId: 'b-' + layer + '-' + i}), (b:Batch {batchId: 'b-' + layer + '-' + (i + 1)}),
                          (x:Batch {batchId: 'b-' + (layer + 1) + '-' + i}), (y:Batch {batchId: 'b-' + (layer + 1) + '-' + (i + 1)})
                    CREATE (a)-[:INPUT_OF]->(t), (b)-[:INPUT_OF]->(t), (t)-[:PRODUCED]->(x), (t)-[:PRODUCED]->(y)""").consume();
            session.run("""
                    UNWIND range(0, 9) AS layer
                    UNWIND range(0, 28) AS i
                    MATCH (a:Batch {batchId: 'b-' + layer + '-' + i}), (b:Batch {batchId: 'b-' + (layer + 1) + '-' + (i + 1)})
                    CREATE (a)-[:SPLIT_INTO]->(b)""").consume();
            session.run("UNWIND range(0, 1) AS i MERGE (c:Company {companyId: 'c' + (i + 1)}) SET c.businessName = 'Empresa ' + (i + 1)").consume();
            session.run("""
                    UNWIND range(0, 29) AS i
                    MATCH (b:Batch {batchId: 'b-0-' + i}), (c:Company {companyId: 'c1'})
                    CREATE (b)-[:TRANSFERRED_VIA {transferId: 'tr-' + i, at: datetime('2026-10-21T12:00:00Z')}]->(c)""").consume();
        }
    }

    @AfterAll
    static void stop() {
        driver.close();
        NEO4J.stop();
    }

    private static List<Operator> plan(String query, Map<String, Object> parameters) {
        try (Session session = driver.session()) {
            List<Operator> operators = new ArrayList<>();
            collect(session.run("EXPLAIN " + query, parameters).consume().plan(), operators);
            return operators;
        }
    }

    private static void collect(Plan plan, List<Operator> into) {
        Value details = plan.arguments().get("Details");
        into.add(new Operator(plan.operatorType(), details == null || details.isNull() ? "" : details.asString()));
        plan.children().forEach(child -> collect(child, into));
    }

    private static void assertNoScans(String name, List<Operator> operators) {
        assertThat(operators).as("%s: sin AllNodesScan, NodeByLabelScan ni NodeIndexScan —recorrer todo un índice es leer todos los lotes— (plan: %s)", name, operators)
                .extracting(Operator::type).noneMatch(type -> type.startsWith("AllNodesScan") || type.startsWith("NodeByLabelScan") || type.startsWith("NodeIndexScan"));
    }

    private static List<String> types(List<Operator> operators) {
        return operators.stream().map(Operator::type).toList();
    }

    private static final List<String> FRONTIER = List.of("b-5-3", "b-5-4", "b-5-5");

    /** Todas las comprobaciones de los planes; se ejecutan con las estadísticas sin muestrear y otra vez después de remuestrearlas. */
    private static void checkEveryPlan(String statistics) {
        List<Operator> qr = plan(LineageQueries.FIND_BY_QR_CODE, Map.of("qr", "QR-5-3"));
        assertNoScans("QR (" + statistics + ")", qr);
        assertThat(qr).as("QR (%s)", statistics).anyMatch(op -> op.type().startsWith("NodeIndexSeek") && op.details().contains("qrCode"));

        List<Operator> exact = plan(LineageQueries.FIND_BY_TRACEABILITY_ID, Map.of("traceabilityId", "TT-5-3"));
        assertNoScans("Traceability ID exacto (" + statistics + ")", exact);
        assertThat(exact).as("Traceability ID exacto (%s)", statistics).anyMatch(op -> op.type().startsWith("NodeUniqueIndexSeek") && op.details().contains("traceabilityId"));

        Map<String, Object> prefixParameters = Map.of("company", "c1", "prefix", "TT-5", "offset", 0L, "limit", 20L);
        for (String[] query : new String[][]{{"prefijo (página)", LineageQueries.SEARCH_BY_PREFIX}, {"prefijo (total)", LineageQueries.COUNT_BY_PREFIX}}) {
            List<Operator> operators = plan(query[1], prefixParameters);
            assertNoScans(query[0] + " (" + statistics + ")", operators);
            assertThat(operators).as("%s (%s): índice compuesto por empresa y Traceability ID", query[0], statistics)
                    .anyMatch(op -> op.type().startsWith("NodeIndexSeek") && op.details().contains("companyId") && op.details().contains("traceabilityId"));
        }

        List<Operator> start = plan(LineageQueries.FIND_BATCH, Map.of("id", "b-5-3"));
        assertNoScans("lote de partida (" + statistics + ")", start);
        assertThat(types(start)).anyMatch(type -> type.startsWith("NodeUniqueIndexSeek"));

        for (String[] query : new String[][]{{"padres por división", LineageQueries.PARENTS_BY_SPLIT}, {"padres por transformación", LineageQueries.PARENTS_BY_TRANSFORMATION},
                {"hijos por división", LineageQueries.CHILDREN_BY_SPLIT}, {"hijos por transformación", LineageQueries.CHILDREN_BY_TRANSFORMATION}}) {
            List<Operator> operators = plan(query[1], Map.of("ids", FRONTIER));
            assertNoScans(query[0] + " (" + statistics + ")", operators);
            assertThat(types(operators)).as("%s (%s): parte de la frontera por restricción de unicidad", query[0], statistics).anyMatch(type -> type.startsWith("NodeUniqueIndexSeek"));
            assertThat(types(operators)).as("%s (%s): la frontera entra como lista", query[0], statistics).anyMatch(type -> type.startsWith("Unwind"));
        }

        for (String[] query : new String[][]{{"historial", LineageQueries.TRANSFERS}, {"nombres", LineageQueries.BUSINESS_NAMES}}) {
            List<Operator> operators = plan(query[1], Map.of("ids", List.of(UUID.randomUUID().toString(), "c1")));
            assertNoScans(query[0] + " (" + statistics + ")", operators);
            assertThat(types(operators)).anyMatch(type -> type.startsWith("NodeUniqueIndexSeek"));
        }
    }

    @Test
    @Order(1)
    void theQueriesNeverScanRightAfterLoadingDataWhenTheStatisticsAreNotSampledYet() {
        checkEveryPlan("estadísticas sin muestrear");
    }

    @Test
    @Order(2)
    void theQueriesNeverScanAfterTheStatisticsAreResampledEither() {
        try (Session session = driver.session()) {
            session.run("CALL db.resampleOutdatedIndexes()").consume();
            session.run("CALL db.awaitIndexes(60)").consume();
        }

        checkEveryPlan("estadísticas remuestreadas");
    }
}
