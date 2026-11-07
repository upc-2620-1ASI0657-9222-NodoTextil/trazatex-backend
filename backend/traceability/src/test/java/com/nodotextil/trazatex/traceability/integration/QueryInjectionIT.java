package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.application.port.in.QueryLineageUseCase;
import com.nodotextil.trazatex.traceability.application.port.out.LineageReadPort;
import com.nodotextil.trazatex.traceability.domain.model.graph.BatchData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las consultas son texto fijo y todo valor del usuario entra como parámetro: un QR o un Traceability ID con comillas y caracteres de
 * Cypher es solo un texto raro. Se prueba que (1) un lote cuyo QR y Traceability ID son exactamente esos textos se encuentra por ellos,
 * (2) buscar esos textos cuando no existen no da error ni devuelve nada, y (3) el grafo y sus restricciones quedan intactos.
 */
class QueryInjectionIT extends IntegrationTestBase {

    static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final List<String> HOSTILE = List.of(
            "QR'\"}) RETURN 1 //",
            "' OR 1=1 //",
            "\"}) MATCH (n) DETACH DELETE n //",
            "`) MATCH (x) DETACH DELETE x //",
            "'; DROP CONSTRAINT batch_id_unique; //",
            "}) RETURN b UNION MATCH (b:Batch) RETURN properties(b) AS batch //",
            "\\' \\\\ $ids $qr $prefix",
            "TT-%_*?\u0000\n\r\t",
            "\" OR b.traceabilityId STARTS WITH \"");

    @Autowired
    LineageReadPort port;
    @Autowired
    QueryLineageUseCase queries;

    private void createBatch(UUID id, String traceabilityId, String qrCode) {
        neo4j.query("""
                CREATE (:Batch {batchId: $id, traceabilityId: $trace, qrCode: $qr, companyId: $company, materialType: 'Algodón', phase: 'AVAILABLE',
                  qualityStatus: 'NOT_REVIEWED', qualityHold: false, finalProduct: false, createdAt: datetime('2026-10-21T10:00:00Z'), provisional: false})""")
                .bind(id.toString()).to("id").bind(traceabilityId).to("trace").bind(qrCode).to("qr").bind(COMPANY.toString()).to("company").run();
    }

    private long batches() {
        return (Long) neo4j.query("MATCH (b:Batch) RETURN count(b) AS n").fetch().one().orElseThrow().get("n");
    }

    private long constraints() {
        return (Long) neo4j.query("SHOW CONSTRAINTS YIELD name RETURN count(name) AS n").fetch().one().orElseThrow().get("n");
    }

    @Test
    void aBatchWhoseCodesAreHostileTextIsFoundByExactlyThoseTexts() {
        for (int i = 0; i < HOSTILE.size(); i++) {
            createBatch(UUID.randomUUID(), "TT-" + HOSTILE.get(i), HOSTILE.get(i));
        }

        for (String text : HOSTILE) {
            assertThat(port.findByQrCode(text)).as("QR %s", text).hasValueSatisfying(batch -> assertThat(batch.qrCode()).isEqualTo(text));
            assertThat(port.findByTraceabilityId("TT-" + text)).as("Traceability ID de %s", text).hasValueSatisfying(batch -> assertThat(batch.traceabilityId()).isEqualTo("TT-" + text));
            assertThat(port.searchByTraceabilityPrefix(COMPANY, "TT-" + text, 0, 10).items()).as("prefijo %s", text).extracting(BatchData::traceabilityId).contains("TT-" + text);
        }
        assertThat(batches()).isEqualTo(HOSTILE.size());
    }

    @Test
    void searchingHostileTextThatDoesNotExistFindsNothingAndDamagesNothing() {
        createBatch(UUID.randomUUID(), "TT-REAA1", "QR-REAA1");
        createBatch(UUID.randomUUID(), "TT-REAA2", "QR-REAA2");
        long constraintsBefore = constraints();

        for (String text : HOSTILE) {
            assertThat(port.findByQrCode(text)).as("QR %s", text).isEmpty();
            assertThat(port.findByTraceabilityId(text)).as("Traceability ID %s", text).isEmpty();
            LineageReadPort.BatchPage page = port.searchByTraceabilityPrefix(COMPANY, text, 0, 10);
            assertThat(page.items()).as("prefijo %s", text).isEmpty();
            assertThat(page.total()).isZero();
        }

        assertThat(batches()).as("ningún lote se perdió").isEqualTo(2);
        assertThat(constraints()).as("ninguna restricción se perdió").isEqualTo(constraintsBefore);
    }

    @Test
    void theSameThroughTheUseCaseWhichNormalizesWhatIsTyped() {
        createBatch(UUID.randomUUID(), "TT-REAA1", "QR-REAA1");
        long constraintsBefore = constraints();

        for (String text : HOSTILE) {
            assertThat(queries.search(COMPANY, text, null, null, null).items()).as("QR %s", text).isEmpty();
            assertThat(queries.search(COMPANY, null, text, null, null).items()).as("Traceability ID %s", text).isEmpty();
            assertThat(queries.search(COMPANY, text, text, 0, 100).totalElements()).isZero();
        }

        assertThat(batches()).isEqualTo(1);
        assertThat(constraints()).isEqualTo(constraintsBefore);
        assertThat(queries.search(COMPANY, "QR-REAA1", null, null, null).items()).hasSize(1);
    }
}
