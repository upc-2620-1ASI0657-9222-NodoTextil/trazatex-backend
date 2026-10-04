package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import com.nodotextil.trazatex.traceability.domain.LineageEdge;
import com.nodotextil.trazatex.traceability.domain.LineageGraph;
import com.nodotextil.trazatex.traceability.domain.LineageRepository;
import com.nodotextil.trazatex.traceability.domain.LineageType;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

@Repository
class Neo4jLineageRepository implements LineageRepository {

    private static final int MAX_DEPTH = 50;

    private static final String RETURN_EDGES = """
            UNWIND relationships(p) AS r
            RETURN DISTINCT startNode(r).batchId AS parent,
                            endNode(r).batchId AS child,
                            r.type AS type,
                            r.occurredAt AS occurredAt
            """;

    private final Neo4jClient client;

    Neo4jLineageRepository(Neo4jClient client) {
        this.client = client;
    }

    @Override
    public void saveEdge(LineageEdge edge) {
        client.query("""
                MERGE (p:Lot {batchId: $parent})
                MERGE (c:Lot {batchId: $child})
                MERGE (p)-[r:GENERATES {type: $type}]->(c)
                ON CREATE SET r.occurredAt = $occurredAt
                """)
                .bind(edge.parentBatchId().toString()).to("parent")
                .bind(edge.childBatchId().toString()).to("child")
                .bind(edge.type().name()).to("type")
                .bind(edge.occurredAt()).to("occurredAt")
                .run();
    }

    @Override
    public LineageGraph findAncestors(UUID batchId) {
        return query(batchId, """
                MATCH p = (:Lot)-[:GENERATES*1..%d]->(:Lot {batchId: $id})
                """.formatted(MAX_DEPTH) + RETURN_EDGES);
    }

    @Override
    public LineageGraph findDescendants(UUID batchId) {
        return query(batchId, """
                MATCH p = (:Lot {batchId: $id})-[:GENERATES*1..%d]->(:Lot)
                """.formatted(MAX_DEPTH) + RETURN_EDGES);
    }

    @Override
    public LineageGraph findFamily(UUID batchId) {
        return query(batchId, """
                MATCH (l:Lot {batchId: $id})
                OPTIONAL MATCH (a:Lot)-[:GENERATES*0..%d]->(l)
                WITH collect(DISTINCT a) AS roots
                UNWIND roots AS root
                MATCH p = (root)-[:GENERATES*1..%d]->(:Lot)
                """.formatted(MAX_DEPTH, MAX_DEPTH) + RETURN_EDGES);
    }

    private LineageGraph query(UUID rootBatchId, String cypher) {
        Collection<LineageEdge> edges = client.query(cypher)
                .bind(rootBatchId.toString()).to("id")
                .fetchAs(LineageEdge.class)
                .mappedBy((typeSystem, record) -> new LineageEdge(
                        UUID.fromString(record.get("parent").asString()),
                        UUID.fromString(record.get("child").asString()),
                        LineageType.valueOf(record.get("type").asString()),
                        record.get("occurredAt").asLocalDateTime()))
                .all();
        return new LineageGraph(rootBatchId, new HashSet<>(edges));
    }
}
