package com.nodotextil.trazatex.traceability.integration;

import org.springframework.data.neo4j.core.Neo4jClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * El grafo de una corrida en forma canónica: una línea por nodo y por relación, con los ids sustituidos por sus nombres
 * ({@code H1}, {@code cA}...) y las propiedades ordenadas. Dos grafos son iguales si sus líneas, ordenadas, lo son. El mismo
 * formato lo usa {@link OrderScenario} para escribir a mano el grafo esperado, de modo que la comparación no es contra otra
 * corrida del mismo código.
 */
final class GraphSnapshot {

    private GraphSnapshot() {
    }

    static String node(String label, String name, Map<String, ?> properties) {
        return "NODE " + label + " " + name + " " + formatted(properties, Map.of());
    }

    static String relationship(String type, String from, String to, Map<String, ?> properties) {
        return "REL " + type + " " + from + " -> " + to + " " + formatted(properties, Map.of());
    }

    /** Lo que hay en la base para las entidades de una corrida (identificadas por sus ids), ordenado. */
    static List<String> of(Neo4jClient neo4j, Collection<UUID> entityIds, Map<String, String> names) {
        List<String> ids = entityIds.stream().map(UUID::toString).toList();
        List<String> lines = new ArrayList<>();
        for (String[] kind : new String[][]{{"Batch", "batchId"}, {"Transformation", "transformationId"}, {"Company", "companyId"}}) {
            String label = kind[0];
            String key = kind[1];
            neo4j.query("MATCH (n:" + label + ") WHERE n." + key + " IN $ids RETURN n." + key + " AS id, properties(n) AS p")
                    .bind(ids).to("ids").fetch().all().forEach(row -> lines.add(
                            "NODE " + label + " " + names.get(row.get("id").toString()) + " " + formatted(castMap(row.get("p")), names)));
            neo4j.query("MATCH (a:" + label + ")-[r]->(b) WHERE a." + key + " IN $ids "
                            + "RETURN type(r) AS type, a." + key + " AS fromId, coalesce(b.batchId, b.transformationId, b.companyId) AS toId, properties(r) AS p")
                    .bind(ids).to("ids").fetch().all().forEach(row -> lines.add(
                            "REL " + row.get("type") + " " + names.get(row.get("fromId").toString()) + " -> "
                                    + names.get(row.get("toId").toString()) + " " + formatted(castMap(row.get("p")), names)));
        }
        lines.sort(String::compareTo);
        return lines;
    }

    private static String formatted(Map<String, ?> properties, Map<String, String> names) {
        StringBuilder text = new StringBuilder("{");
        new TreeMap<String, Object>(properties).forEach((key, value) -> {
            if (text.length() > 1) {
                text.append("; ");
            }
            text.append(key).append('=').append(value(value, names));
        });
        return text.append('}').toString();
    }

    private static String value(Object value, Map<String, String> names) {
        if (value instanceof ZonedDateTime zoned) {
            return zoned.toInstant().toString();
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant().toString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        String text = String.valueOf(value);
        return names.getOrDefault(text, text);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
