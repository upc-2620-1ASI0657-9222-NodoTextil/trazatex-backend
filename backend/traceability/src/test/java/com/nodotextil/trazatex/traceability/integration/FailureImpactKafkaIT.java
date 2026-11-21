package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.integration.EventMessages.Message;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nodotextil.trazatex.traceability.integration.ImpactKafkaSupport.GROUP;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"trazatex.messaging.enabled=true", "trazatex.messaging.consumer.topic-check-interval=PT0.5S", "trazatex.messaging.relay-delay=PT0.2S",
        "trazatex.messaging.consumer.impact-retry.max-retries=10", "trazatex.messaging.consumer.impact-retry.initial-interval=PT0.05S",
        "trazatex.messaging.consumer.impact-retry.multiplier=2.0", "trazatex.messaging.consumer.impact-retry.max-interval=PT2S",
        "trazatex.messaging.consumer.max-poll-interval=PT10S"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FailureImpactKafkaIT extends IntegrationTestBase {

    static final KafkaContainer KAFKA = KafkaTestSupport.startKafka();
    static final KafkaProducer<String, String> PRODUCER;
    static final Duration PATIENCE = Duration.ofSeconds(60);
    static final UUID COMPANY_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    static {
        KafkaTestSupport.createTopics(KAFKA.getBootstrapServers(), ImpactKafkaSupport.FOREIGN_TOPICS);
        PRODUCER = KafkaTestSupport.producer(KAFKA.getBootstrapServers());
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("trazatex.messaging.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    ConsumerFactory<String, String> consumerFactory;
    @Autowired
    Environment environment;

    @Override
    @BeforeEach
    void emptyGraph() {
    }

    private static String bootstrap() {
        return KAFKA.getBootstrapServers();
    }

    private void send(Message... messages) {
        ImpactKafkaSupport.sendAndAwait(PRODUCER, neo4j, PATIENCE, List.of(messages));
    }

    private static int count(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @Test
    @Order(1)
    void aFailureConfirmedPublishesPossibleDerivedFailureDetectedOnceWithTheExactPayloadAndNothingWhenThereIsNoOneToReview() {
        UUID f = UUID.randomUUID();
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        UUID x = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        send(EventMessages.batchRegistered(UUID.randomUUID(), f, "TT-F" + f.toString().substring(0, 6), "QR-F" + f.toString().substring(0, 6), ImpactKafkaSupport.COMPANY, "Algodón", 100, "Piura",
                "Textiles del Norte", "Fibra limpia", GraphBuilder.T0));
        send(EventMessages.batchSplit(UUID.randomUUID(), f, ImpactKafkaSupport.COMPANY, List.of(
                EventMessages.splitChild(f1, "TT-A" + f1.toString().substring(0, 6), "QR-A" + f1.toString().substring(0, 6), "Algodón", 50, "Piura", "Textiles del Norte", "Fibra limpia",
                        "NOT_REVIEWED", GraphBuilder.T0.plusSeconds(60)),
                EventMessages.splitChild(f2, "TT-B" + f2.toString().substring(0, 6), "QR-B" + f2.toString().substring(0, 6), "Algodón", 50, "Piura", "Textiles del Norte", "Fibra limpia",
                        "NOT_REVIEWED", GraphBuilder.T0.plusSeconds(60)))),
                EventMessages.phaseChanged(UUID.randomUUID(), f1, ImpactKafkaSupport.COMPANY, "AVAILABLE", "PROCESSED", false, 2),
                EventMessages.batchRegistered(UUID.randomUUID(), x, "TT-X" + x.toString().substring(0, 6), "QR-X" + x.toString().substring(0, 6), COMPANY_B, "Algodón", 100, "Piura",
                        "Otro proveedor", "Fibra limpia", GraphBuilder.T0));
        send(ImpactKafkaSupport.transformation(UUID.randomUUID(), List.of(f1, x), List.of(u)));

        UUID failure = UUID.randomUUID();
        Message first = ImpactKafkaSupport.failureConfirmed(UUID.randomUUID(), failure, f);
        send(first);

        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(ImpactKafkaSupport.impactOf(bootstrap(), failure, Duration.ofSeconds(1))).hasSize(1));
        ConsumerRecord<String, String> published = ImpactKafkaSupport.impactOf(bootstrap(), failure, Duration.ofSeconds(1)).getFirst();
        assertThat(published.key()).as("la clave del mensaje es la falla").isEqualTo(failure.toString());
        JsonNode envelope = ImpactKafkaSupport.JSON.readTree(published.value());
        assertThat(envelope.get("type").asString()).isEqualTo("PossibleDerivedFailureDetected");
        assertThat(envelope.get("payload").propertyNames()).containsExactlyInAnyOrder("failureId", "sourceBatchId", "affected");
        assertThat(envelope.get("payload").get("sourceBatchId").asString()).isEqualTo(f.toString());
        assertThat(ImpactKafkaSupport.affectedOf(published)).as("F2 y U, con F como antecesor común").containsOnlyKeys(f2, u).containsValues(f);
        assertThat(ImpactKafkaSupport.affectedOf(published).values()).containsOnly(f);

        Message again = ImpactKafkaSupport.failureConfirmed(UUID.randomUUID(), failure, f);
        KafkaTestSupport.send(PRODUCER, first);
        send(again);
        assertThat(ImpactKafkaSupport.impactOf(bootstrap(), failure, Duration.ofSeconds(3))).as("una sola publicación por falla").hasSize(1);
        assertThat(neo4j.query("MATCH (o:OutboxEvent {aggregateId: $f}) RETURN o.status AS status").bind(failure.toString()).to("f").fetch().all())
                .extracting(row -> row.get("status")).containsExactly("PUBLISHED");

        UUID alone = UUID.randomUUID();
        UUID emptyFailure = UUID.randomUUID();
        send(EventMessages.batchRegistered(UUID.randomUUID(), alone, "TT-Z" + alone.toString().substring(0, 6), "QR-Z" + alone.toString().substring(0, 6), ImpactKafkaSupport.COMPANY, "Algodón",
                100, "Piura", "Textiles del Norte", "Fibra limpia", GraphBuilder.T0));
        send(ImpactKafkaSupport.failureConfirmed(UUID.randomUUID(), emptyFailure, alone));
        assertThat(ImpactKafkaSupport.impactOf(bootstrap(), emptyFailure, Duration.ofSeconds(3))).as("lista vacía: no se publica nada").isEmpty();
    }

    @Test
    @Order(2)
    void theTopicsAreDeclaredByTraceabilityWithoutDeletionByTimeAndTheDeadLetterTopicOfTheFailureTopicExists() {
        assertThat(KafkaTestSupport.topics(bootstrap())).contains("traceability.impact", "quality.failure.DLT").doesNotContain("quality.control");
        assertThat(ImpactKafkaSupport.retentionOf(bootstrap(), "traceability.impact")).as("sin borrado por tiempo, como los demás tópicos de eventos").isEqualTo("-1");
        assertThat(ImpactKafkaSupport.partitionsOf(bootstrap(), "traceability.impact")).isEqualTo(1);
    }

    @Test
    @Order(3)
    void theConsumerKeepsTheProportionBetweenTheLongestWaitAndMaxPollInterval() {
        Duration longest = Duration.parse(environment.getProperty("trazatex.messaging.consumer.impact-retry.max-interval"));
        Object maxPollMs = consumerFactory.getConfigurationProperties().get("max.poll.interval.ms");

        assertThat(maxPollMs).as("max.poll.interval.ms explícito en el consumidor").isEqualTo(10_000);
        assertThat(Duration.ofMillis(10_000).dividedBy(longest)).as("una quinta parte, como en producción (60 s contra 5 min)").isEqualTo(5);
    }

    @Test
    @Order(4)
    void aFamilyThatCompletesWhileTheEventIsBeingRetriedIsPublishedOnceWithoutTheDeadLetterTopicOrALeaveFromTheGroup(CapturedOutput output) {
        UUID p = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        UUID o2 = UUID.randomUUID();
        send(ImpactKafkaSupport.transformation(UUID.randomUUID(), List.of(p), List.of(s, o2)));
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> ImpactKafkaSupport.members(bootstrap()).size() == 1);
        Set<String> membersBefore = ImpactKafkaSupport.members(bootstrap());
        int revokedBefore = count(output.getAll(), "traceability-quality-failure: partitions revoked");
        UUID failure = UUID.randomUUID();

        KafkaTestSupport.send(PRODUCER, ImpactKafkaSupport.failureConfirmed(UUID.randomUUID(), failure, s));
        sleep(1200);
        assertThat(ImpactKafkaSupport.impactOf(bootstrap(), failure, Duration.ofSeconds(1))).as("incompleta: no se publica un resultado parcial").isEmpty();
        send(ImpactKafkaSupport.registeredAndProcessed(p).toArray(Message[]::new));

        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(ImpactKafkaSupport.impactOf(bootstrap(), failure, Duration.ofSeconds(1))).hasSize(1));
        assertThat(ImpactKafkaSupport.affectedOf(ImpactKafkaSupport.impactOf(bootstrap(), failure, Duration.ofSeconds(1)).getFirst())).as("O2, hijo de P").containsOnlyKeys(o2)
                .containsValue(p);
        assertThat(ImpactKafkaSupport.impactOf(bootstrap(), failure, Duration.ofSeconds(2))).as("una sola vez").hasSize(1);
        assertThat(KafkaTestSupport.readAll(bootstrap(), "quality.failure.DLT", Duration.ofSeconds(1)).stream().filter(r -> failure.toString().equals(r.key()))).isEmpty();
        assertThat(ImpactKafkaSupport.members(bootstrap())).as("el mismo miembro: no salió del grupo").isEqualTo(membersBefore);
        assertThat(count(output.getAll(), "traceability-quality-failure: partitions revoked")).as("ningún rebalanceo").isEqualTo(revokedBefore);
    }

    @Test
    @Order(5)
    void theTenRetriesAddUpToMoreThanMaxPollIntervalAndTheConsumerNeitherLeavesTheGroupNorTriggersARebalance(CapturedOutput output) {
        UUID p = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        UUID o2 = UUID.randomUUID();
        send(ImpactKafkaSupport.transformation(UUID.randomUUID(), List.of(p), List.of(s, o2)));
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> ImpactKafkaSupport.members(bootstrap()).size() == 1);
        Set<String> membersBefore = ImpactKafkaSupport.members(bootstrap());
        int revokedBefore = count(output.getAll(), "traceability-quality-failure: partitions revoked");
        UUID failure = UUID.randomUUID();
        long start = System.nanoTime();

        KafkaTestSupport.send(PRODUCER, ImpactKafkaSupport.failureConfirmed(UUID.randomUUID(), failure, s));
        List<ConsumerRecord<String, String>> deadLetter = new ArrayList<>();
        Awaitility.await().atMost(PATIENCE).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            deadLetter.clear();
            KafkaTestSupport.readAll(bootstrap(), "quality.failure.DLT", Duration.ofMillis(500)).stream().filter(r -> failure.toString().equals(r.key())).forEach(deadLetter::add);
            assertThat(deadLetter).hasSize(1);
        });
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).as("los reintentos suman más que max.poll.interval.ms (10 s): ≈ 11,15 s de espera").isGreaterThanOrEqualTo(Duration.ofSeconds(10));
        assertThat(elapsed).as("son los reintentos PROPIOS (10, de 0,05 a 2 s: ≈ 11 s), no los generales (5, de 1 a 16 s: ≈ 31 s)").isLessThan(Duration.ofSeconds(25));
        assertThat(ImpactKafkaSupport.members(bootstrap())).as("el mismo miembro: el broker no lo expulsó ni se volvió a unir").isEqualTo(membersBefore);
        assertThat(ImpactKafkaSupport.groupState(bootstrap())).as("el grupo sigue estable").isEqualToIgnoringCase("Stable");
        assertThat(count(output.getAll(), "traceability-quality-failure: partitions revoked")).as("ningún rebalanceo").isEqualTo(revokedBefore);
        assertThat(output.getAll()).as("Kafka no registró una expulsión por poll").doesNotContain("poll timeout has expired");
        assertThat(ImpactKafkaSupport.impactOf(bootstrap(), failure, Duration.ofSeconds(1))).as("nunca se publicó un resultado parcial").isEmpty();
        assertThat(output.getAll()).contains("HAY LOTES POSIBLEMENTE AFECTADOS SIN RETENER").contains(failure.toString()).contains(p.toString())
                .contains("tras 11 intentos");

        UUID q = UUID.randomUUID();
        UUID qs = UUID.randomUUID();
        UUID qo = UUID.randomUUID();
        send(ImpactKafkaSupport.registeredAndProcessed(q).toArray(Message[]::new));
        send(ImpactKafkaSupport.transformation(UUID.randomUUID(), List.of(q), List.of(qs, qo)));
        UUID next = UUID.randomUUID();
        send(ImpactKafkaSupport.failureConfirmed(UUID.randomUUID(), next, qs));
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(ImpactKafkaSupport.impactOf(bootstrap(), next, Duration.ofSeconds(1))).hasSize(1));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
