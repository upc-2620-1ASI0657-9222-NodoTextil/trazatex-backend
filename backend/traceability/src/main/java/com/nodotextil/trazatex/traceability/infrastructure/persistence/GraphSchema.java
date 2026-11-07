package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import java.util.List;

/**
 * Restricciones e índices del grafo (docs/architecture/data.md). La edición community de Neo4j no tiene restricciones de
 * existencia ({@code IS NOT NULL}): lo obligatorio lo valida el código de los consumidores.
 */
public final class GraphSchema {

    /** nombre → sentencia. La unicidad también crea el índice que usan los {@code MERGE}. */
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
            // Buscar una genealogía por QR (US30). No es única en el grafo: un lote provisional no tiene QR todavía
            new Statement("batch_qr_code", "CREATE INDEX batch_qr_code IF NOT EXISTS FOR (b:Batch) ON (b.qrCode)"),
            // Buscar por prefijo del Traceability ID dentro de una empresa (US30): companyId = $c AND traceabilityId STARTS WITH $p
            new Statement("batch_company_traceability", "CREATE INDEX batch_company_traceability IF NOT EXISTS FOR (b:Batch) ON (b.companyId, b.traceabilityId)"),
            new Statement("outbox_event_aggregate", "CREATE INDEX outbox_event_aggregate IF NOT EXISTS FOR (o:OutboxEvent) ON (o.aggregateId, o.eventType)"),
            new Statement("outbox_event_pending", "CREATE INDEX outbox_event_pending IF NOT EXISTS FOR (o:OutboxEvent) ON (o.status, o.createdAt)"));

    private GraphSchema() {
    }
}
