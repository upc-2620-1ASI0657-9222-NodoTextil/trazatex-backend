package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import com.nodotextil.trazatex.traceability.application.port.out.QualityDetailsPort;
import com.nodotextil.trazatex.traceability.domain.model.graph.QualityTestValue;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Los detalles de calidad como propiedades del {@code :Batch}: {@code qualityTests} (JSON) y {@code failureCause}, cada uno con el instante del evento que lo dejó. */
@Component
class Neo4jQualityDetailsAdapter implements QualityDetailsPort {

    private final Neo4jClient neo4j;
    private final ObjectMapper mapper;

    Neo4jQualityDetailsAdapter(Neo4jClient neo4j, ObjectMapper mapper) {
        this.neo4j = neo4j;
        this.mapper = mapper;
    }

    @Override
    public void saveTests(UUID batchId, Instant occurredAt, List<QualityTestValue> tests) {
        neo4j.query("""
                MERGE (b:Batch {batchId:$batch}) ON CREATE SET b.provisional=true
                WITH b WHERE b.qualityTestsAt IS NULL OR b.qualityTestsAt <= datetime($at)
                SET b.qualityTests=$tests,b.qualityTestsAt=datetime($at)
                """).bind(batchId.toString()).to("batch").bind(occurredAt.toString()).to("at").bind(mapper.writeValueAsString(tests)).to("tests").run();
    }

    @Override
    public void saveFailureCause(UUID batchId, Instant occurredAt, String cause) {
        neo4j.query("""
                MERGE (b:Batch {batchId:$batch}) ON CREATE SET b.provisional=true
                WITH b WHERE b.failureCauseAt IS NULL OR b.failureCauseAt <= datetime($at)
                SET b.failureCause=$cause,b.failureCauseAt=datetime($at)
                """).bind(batchId.toString()).to("batch").bind(occurredAt.toString()).to("at").bind(cause).to("cause").run();
    }
}
