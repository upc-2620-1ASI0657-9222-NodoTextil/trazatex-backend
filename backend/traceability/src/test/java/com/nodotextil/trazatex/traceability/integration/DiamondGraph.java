package com.nodotextil.trazatex.traceability.integration;

import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;
import java.util.UUID;

/**
 * El grafo en forma de diamante de RNF-04: 11 capas de 30 lotes (330) unidas por 170 transformaciones con 2 entradas y 2 salidas (10 capas × 17) y por
 * divisiones: 500 nodos y muchísimos caminos (cada lote tiene varios padres y varios hijos). Con dos empresas, los lotes pares son de la A y los impares de la B. Los
 * ids son UUID derivados de (capa, posición): {@code 00000000-0000-0000-<capa>-<posición>}. Se carga con Cypher (no por los casos de uso: serían
 * ~700 transacciones) con todos los campos que lee una consulta.
 */
final class DiamondGraph {

    static final int LAYERS = 11;
    static final int WIDTH = 30;

    private DiamondGraph() {
    }

    static UUID batchId(int layer, int position) {
        return UUID.fromString(String.format("00000000-0000-0000-%04d-%012d", layer, position));
    }

    private static String id(String layer, String position) {
        return "'00000000-0000-0000-' + right('0000' + toString(" + layer + "), 4) + '-' + right('000000000000' + toString(" + position + "), 12)";
    }

    /** Carga el grafo; {@code companyA} es dueña de los lotes en posición par y {@code companyB} de los impares. */
    static void load(Neo4jClient neo4j, UUID companyA, UUID companyB) {
        load(neo4j, List.of(companyA, companyB));
    }

    /**
     * Carga el grafo con varias empresas: el lote en la posición {@code i} es de {@code owners[i % owners.size()]}; todas las transformaciones, de la primera
     * ({@code owners[0]}, la que consulta en las mediciones).
     */
    static void load(Neo4jClient neo4j, List<UUID> owners) {
        neo4j.query("""
                UNWIND range(0, 10) AS layer
                UNWIND range(0, 29) AS i
                CREATE (:Batch {batchId: %s, traceabilityId: 'TT-' + layer + '-' + i, qrCode: 'QR-' + layer + '-' + i,
                  companyId: $owners[i %% size($owners)], materialType: 'Algodón', phase: 'AVAILABLE', qualityStatus: 'NOT_REVIEWED', qualityHold: false,
                  finalProduct: false, createdAt: datetime('2026-10-21T10:00:00Z'), provisional: false, supplierName: 'P', origin: 'Piura', quantityKg: '10.000',
                  composition: '[{"name":"Algodón","percentage":100.00}]', receptionCharacteristics: 'Seco'})""".formatted(id("layer", "i")))
                .bind(owners.stream().map(UUID::toString).toList()).to("owners").run();
        neo4j.query("""
                UNWIND range(0, 9) AS layer
                UNWIND range(0, 16) AS i
                CREATE (t:Transformation {transformationId: '11111111-1111-1111-' + right('0000' + toString(layer), 4) + '-' + right('000000000000' + toString(i), 12),
                  companyId: $a, type: 'DYEING', startedAt: datetime('2026-10-21T10:00:00Z'), completedAt: datetime('2026-10-21T11:00:00Z'), provisional: false,
                  machineName: 'M', machineType: 'T', shrinkageKg: '1.000', wasteKg: '1.000', wasteReason: 'O'})
                WITH t, layer, i
                MATCH (a:Batch {batchId: %s}), (b:Batch {batchId: %s}), (x:Batch {batchId: %s}), (y:Batch {batchId: %s})
                CREATE (a)-[:INPUT_OF]->(t), (b)-[:INPUT_OF]->(t), (t)-[:PRODUCED]->(x), (t)-[:PRODUCED]->(y)""".formatted(id("layer", "i"), id("layer", "i + 1"),
                id("layer + 1", "i"), id("layer + 1", "i + 1"))).bind(owners.getFirst().toString()).to("a").run();
        neo4j.query("""
                UNWIND range(0, 9) AS layer
                UNWIND range(0, 28) AS i
                MATCH (a:Batch {batchId: %s}), (b:Batch {batchId: %s})
                CREATE (a)-[:SPLIT_INTO]->(b)""".formatted(id("layer", "i"), id("layer + 1", "i + 1"))).run();
    }

    /** Los nodos del grafo (lotes y transformaciones): deben ser 500. */
    static long nodeCount(Neo4jClient neo4j) {
        return (Long) neo4j.query("MATCH (n) WHERE n:Batch OR n:Transformation RETURN count(n) AS n").fetch().one().orElseThrow().get("n");
    }
}
