package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.integration.EventMessages.Message;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirma contra un Kafka real el modelo de la prueba exhaustiva ({@link LineageOrderIT}): cada (tópico, clave) es un flujo
 * cuyo orden se conserva y entre flujos no hay orden. Los mismos 8 eventos del caso pequeño, como mensajes del catálogo, se
 * publican en sus tópicos en órdenes entre tópicos distintos del natural y el servicio (cinco consumidores en hilos
 * distintos) debe dejar el grafo esperado. {@code production.batch} y {@code quality.control} tienen 3 particiones para que
 * las claves distintas de un mismo tópico también puedan llegar mezcladas.
 */
@SpringBootTest(properties = {"trazatex.messaging.enabled=true", "trazatex.messaging.consumer.topic-check-interval=PT0.5S",
        "trazatex.messaging.consumer.retry.max-retries=2", "trazatex.messaging.consumer.retry.initial-interval=PT0.2S",
        "trazatex.messaging.consumer.retry.max-interval=PT0.5S"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaOrderIT extends IntegrationTestBase {

    static final KafkaContainer KAFKA = KafkaTestSupport.startKafka();
    static final KafkaProducer<String, String> PRODUCER;
    static final long SEED = 20261021L;
    static final Duration PATIENCE = Duration.ofSeconds(60);

    static {
        KafkaTestSupport.createTopics(KAFKA.getBootstrapServers(), Map.of("production.batch", 3, "production.transformation", 1,
                "production.transfer", 1, "orgaccess.company", 1, "quality.control", 3));
        PRODUCER = KafkaTestSupport.producer(KAFKA.getBootstrapServers());
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("trazatex.messaging.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private OrderScenario.Instance caseFor(int run) {
        return OrderScenario.small(run, null);
    }

    /** Publica de uno en uno esperando a que el servicio procese cada uno: el orden de llegada entre tópicos es exactamente este. */
    private void deliverOneByOne(OrderScenario.Instance instance, String... order) {
        for (String name : order) {
            Message message = instance.messages().get(name);
            KafkaTestSupport.send(PRODUCER, message);
            GraphAssertions.awaitProcessed(neo4j, List.of(message.eventId()), PATIENCE);
        }
    }

    @Test
    void theNaturalAndTheDifficultOrdersGiveTheExpectedGraphWhenKafkaDeliversThemOneByOne() {
        int run = 7000;
        for (List<String> order : OrderScenario.NAMED_ORDERS) {
            OrderScenario.Instance instance = caseFor(run++);

            deliverOneByOne(instance, order.toArray(String[]::new));

            GraphAssertions.assertExpectedGraph(neo4j, instance);
        }
    }

    @Test
    void randomInterleavingsOfTheFlowsGiveTheExpectedGraphWhenKafkaDeliversThemOneByOne() {
        OrderScenario.Instance sample = caseFor(7100);
        List<int[]> all = Interleavings.all(sample.streamLengths());
        for (int i = 0; i < 12; i++) {
            OrderScenario.Instance instance = caseFor(7100 + i);
            int[] order = all.get(Interleavings.seeded(SEED, i).nextInt(all.size()));

            deliverOneByOne(instance, namesOf(instance, order));

            GraphAssertions.assertExpectedGraph(neo4j, instance);
        }
    }

    private static String[] namesOf(OrderScenario.Instance instance, int[] order) {
        return java.util.Arrays.stream(order).mapToObj(index -> instance.events().get(index).name()).toArray(String[]::new);
    }

    @Test
    void aBurstPublishedWithoutWaitingInAnUnnaturalTopicOrderGivesTheExpectedGraph() {
        // los flujos completos en este orden: la transferencia y la transformación primero, el registro del padre al final;
        // dentro de cada flujo, el orden en que ocurrió
        List<String> flows = List.of("TA(TR1>cB)", "TC(T1)", "BPC(H2 AVAILABLE cB v2)", "BPC(H1 IN_TRANSFORMATION v1)", "BPC(H1 PROCESSED v2)",
                "BR(L1)", "BS(L1>H1,H2)", "BPC(L1 SPLIT v1)");
        for (int run = 0; run < 25; run++) {
            OrderScenario.Instance instance = caseFor(7200 + run);
            List<Message> burst = new ArrayList<>(flows.stream().map(instance.messages()::get).toList());
            // la mitad de las corridas, además, con cada mensaje publicado dos veces (at-least-once)
            if (run % 2 == 1) {
                burst.addAll(flows.stream().map(instance.messages()::get).toList());
            }

            KafkaTestSupport.burst(PRODUCER, burst);

            GraphAssertions.awaitProcessed(neo4j, instance.eventIds(), PATIENCE);
            GraphAssertions.assertExpectedGraph(neo4j, instance);
        }
    }

    @Test
    void theOrderOfAKeyIsKeptEvenWhenOtherKeysAndPartitionsAreBusy() {
        // quality.control (3 particiones): el estado de calidad no lleva versión, manda el orden de la clave batchId
        UUID company = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        String[] statuses = {"CONFORMING", "FAILED", "PENDING_REEVALUATION", "POTENTIAL_DERIVED_FAILURE", "NOT_REVIEWED"};
        List<Message> burst = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        Random random = Interleavings.seeded(SEED, 99);
        String last = null;
        for (int i = 0; i < 80; i++) {
            String status = statuses[i % statuses.length];
            Message mine = EventMessages.qualityStatusChanged(UUID.randomUUID(), target, company, "X", status);
            burst.add(mine);
            ids.add(mine.eventId());
            last = status;
            for (int noise = 0; noise < 2; noise++) {
                Message other = EventMessages.qualityStatusChanged(UUID.randomUUID(), UUID.randomUUID(), company, "X", statuses[random.nextInt(5)]);
                burst.add(other);
                ids.add(other.eventId());
            }
        }

        KafkaTestSupport.burst(PRODUCER, burst);

        GraphAssertions.awaitProcessed(neo4j, ids, PATIENCE);
        assertThat(neo4j.query("MATCH (b:Batch {batchId: $id}) RETURN b.qualityStatus AS status").bind(target.toString()).to("id")
                .fetch().one().orElseThrow()).containsEntry("status", last);
    }
}
