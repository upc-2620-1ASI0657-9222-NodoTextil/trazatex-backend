package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import java.util.List;

public final class GraphSchema {

    public record Statement(String name, String cypher) {
    }

    public static final List<Statement> CONSTRAINTS = List.of(
            new Statement("batch_id_unique", "CREATE CONSTRAINT batch_id_unique IF NOT EXISTS FOR (b:Batch) REQUIRE b.batchId IS UNIQUE"),
            new Statement("batch_traceability_id_unique",
                    "CREATE CONSTRAINT batch_traceability_id_unique IF NOT EXISTS FOR (b:Batch) REQUIRE b.traceabilityId IS UNIQUE"),
            new Statement("transformation_id_unique",
                    "CREATE CONSTRAINT transformation_id_unique IF NOT EXISTS FOR (t:Transformation) REQUIRE t.transformationId IS UNIQUE"),
            new Statement("company_id_unique", "CREATE CONSTRAINT company_id_unique IF NOT EXISTS FOR (c:Company) REQUIRE c.companyId IS UNIQUE"),
            new Statement("processed_event_id_unique",
                    "CREATE CONSTRAINT processed_event_id_unique IF NOT EXISTS FOR (p:ProcessedEvent) REQUIRE p.eventId IS UNIQUE"),
            new Statement("outbox_event_id_unique",
                    "CREATE CONSTRAINT outbox_event_id_unique IF NOT EXISTS FOR (o:OutboxEvent) REQUIRE o.eventId IS UNIQUE"));

    public static final List<Statement> INDEXES = List.of(
            new Statement("batch_qr_code", "CREATE INDEX batch_qr_code IF NOT EXISTS FOR (b:Batch) ON (b.qrCode)"),
            new Statement("batch_company_traceability", "CREATE INDEX batch_company_traceability IF NOT EXISTS FOR (b:Batch) ON (b.companyId, b.traceabilityId)"),
            new Statement("outbox_event_aggregate", "CREATE INDEX outbox_event_aggregate IF NOT EXISTS FOR (o:OutboxEvent) ON (o.aggregateId, o.eventType)"),
            new Statement("outbox_event_pending", "CREATE INDEX outbox_event_pending IF NOT EXISTS FOR (o:OutboxEvent) ON (o.status, o.createdAt)"));

    private GraphSchema() {
    }
}
