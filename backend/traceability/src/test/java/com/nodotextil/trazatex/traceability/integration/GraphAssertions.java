package com.nodotextil.trazatex.traceability.integration;

import org.awaitility.Awaitility;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

final class GraphAssertions {

    private GraphAssertions() {
    }

    static long processed(Neo4jClient neo4j, Collection<UUID> eventIds) {
        return (Long) neo4j.query("MATCH (p:ProcessedEvent) WHERE p.eventId IN $ids RETURN count(p) AS n")
                .bind(eventIds.stream().map(Object::toString).toList()).to("ids").fetch().one().orElseThrow().get("n");
    }

    static void awaitProcessed(Neo4jClient neo4j, Collection<UUID> eventIds, Duration timeout) {
        long expected = eventIds.stream().distinct().count();
        Awaitility.await().atMost(timeout).pollInterval(Duration.ofMillis(50)).alias("eventos procesados")
                .untilAsserted(() -> assertThat(processed(neo4j, eventIds)).isEqualTo(expected));
    }

    static void assertExpectedGraph(Neo4jClient neo4j, OrderScenario.Instance instance) {
        List<String> actual = GraphSnapshot.of(neo4j, instance.entityIds(), instance.names());
        List<String> missing = new ArrayList<>(instance.expected());
        actual.forEach(missing::remove);
        List<String> unexpected = new ArrayList<>(actual);
        instance.expected().forEach(unexpected::remove);
        assertThat(missing).as("faltan en el grafo").isEmpty();
        assertThat(unexpected).as("sobran en el grafo").isEmpty();
    }
}
