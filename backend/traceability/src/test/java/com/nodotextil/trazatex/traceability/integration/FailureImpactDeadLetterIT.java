package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.integration.EventMessages.Message;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El reenvío desde la DLT de punta a punta, con Kafka real y el servicio completo: un {@code FailureConfirmed} con la familia incompleta agota sus reintentos y va a
 * {@code quality.failure.DLT} con el {@code ERROR} → llegan los eventos de Production que faltaban → se reenvía el mensaje con <b>el procedimiento escrito en
 * {@code docs/progress.md}</b> (esta prueba lee sus comandos de ese archivo y los ejecuta dentro del contenedor del broker: no hay aquí ningún comando propio) →
 * {@code PossibleDerivedFailureDetected} se publica <b>una sola vez</b>, aunque el procedimiento se repita. Tiempos acortados con la misma proporción que en producción (ver
 * {@link FailureImpactKafkaIT}).
 */
@SpringBootTest(properties = {"trazatex.messaging.enabled=true", "trazatex.messaging.consumer.topic-check-interval=PT0.5S", "trazatex.messaging.relay-delay=PT0.2S",
        "trazatex.messaging.consumer.impact-retry.max-retries=10", "trazatex.messaging.consumer.impact-retry.initial-interval=PT0.05S",
        "trazatex.messaging.consumer.impact-retry.multiplier=2.0", "trazatex.messaging.consumer.impact-retry.max-interval=PT2S",
        "trazatex.messaging.consumer.max-poll-interval=PT10S"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class FailureImpactDeadLetterIT extends IntegrationTestBase {

    static final KafkaContainer KAFKA = KafkaTestSupport.startKafka();
    static final KafkaProducer<String, String> PRODUCER;
    static final Duration PATIENCE = Duration.ofSeconds(90);

    static {
        KafkaTestSupport.createTopics(KAFKA.getBootstrapServers(), ImpactKafkaSupport.FOREIGN_TOPICS);
        PRODUCER = KafkaTestSupport.producer(KAFKA.getBootstrapServers());
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("trazatex.messaging.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    /** No se vacía el grafo entre pruebas: cada una usa ids propios. */
    @Override
    @BeforeEach
    void emptyGraph() {
    }

    private static String bootstrap() {
        return KAFKA.getBootstrapServers();
    }

    private static List<ConsumerRecord<String, String>> deadLetterOf(UUID failure) {
        return KafkaTestSupport.readAll(bootstrap(), "quality.failure.DLT", Duration.ofSeconds(1)).stream().filter(r -> failure.toString().equals(r.key())).toList();
    }

    @Test
    void aMessageThatExhaustedItsRetriesIsResentFromTheDeadLetterTopicWithTheDocumentedProcedureAndIsPublishedOnlyOnce(CapturedOutput output) {
        UUID p = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        UUID o2 = UUID.randomUUID();
        UUID failure = UUID.randomUUID();
        // 1. la transformación llega antes que el registro de su entrada P: la familia de S queda incompleta
        ImpactKafkaSupport.sendAndAwait(PRODUCER, neo4j, PATIENCE, List.of(ImpactKafkaSupport.transformation(UUID.randomUUID(), List.of(p), List.of(s, o2))));
        Message original = ImpactKafkaSupport.failureConfirmed(UUID.randomUUID(), failure, s);
        KafkaTestSupport.send(PRODUCER, original);

        // 2. agota los reintentos: DLT con el mensaje intacto y un ERROR con todo lo necesario para actuar
        List<ConsumerRecord<String, String>> deadLetter = new ArrayList<>();
        Awaitility.await().atMost(PATIENCE).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            deadLetter.clear();
            deadLetter.addAll(deadLetterOf(failure));
            assertThat(deadLetter).hasSize(1);
        });
        assertThat(deadLetter.getFirst().value()).as("el mensaje de la DLT es el original, intacto").isEqualTo(original.json());
        assertThat(output.getAll()).contains("HAY LOTES POSIBLEMENTE AFECTADOS SIN RETENER").contains(failure.toString()).contains(p.toString())
                .contains(RedriveProcedure.commands().get(0)).contains(RedriveProcedure.commands().get(1).replace("<failureId>", failure.toString()));
        assertThat(ImpactKafkaSupport.impactOf(bootstrap(), failure, Duration.ofSeconds(1))).as("no se publicó nada parcial").isEmpty();

        // 3. llegan los eventos de Production que faltaban
        ImpactKafkaSupport.sendAndAwait(PRODUCER, neo4j, PATIENCE, ImpactKafkaSupport.registeredAndProcessed(p));

        // 4. el procedimiento de progress.md, tal cual
        RedriveProcedure.copyScript(KAFKA);
        ExecResult first = RedriveProcedure.run(KAFKA, failure);
        assertThat(first.getExitCode()).as("salida: %s / %s", first.getStdout(), first.getStderr()).isZero();
        assertThat(first.getStdout()).contains("Reenviados 1 mensajes de quality.failure.DLT a quality.failure con la clave " + failure);

        // 5. PossibleDerivedFailureDetected se publica una sola vez
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(ImpactKafkaSupport.impactOf(bootstrap(), failure, Duration.ofSeconds(1))).hasSize(1));
        assertThat(ImpactKafkaSupport.affectedOf(ImpactKafkaSupport.impactOf(bootstrap(), failure, Duration.ofSeconds(1)).getFirst())).containsOnlyKeys(o2).containsValue(p);

        // 6. repetir el procedimiento es seguro: el mensaje conserva su eventId y la falla ya está publicada
        ExecResult second = RedriveProcedure.run(KAFKA, failure);
        assertThat(second.getExitCode()).isZero();
        assertThat(ImpactKafkaSupport.impactOf(bootstrap(), failure, Duration.ofSeconds(4))).as("una sola publicación aunque se reenvíe dos veces").hasSize(1);
        assertThat(deadLetterOf(failure)).as("el mensaje no se borra de la DLT").hasSize(1);
        assertThat(neo4j.query("MATCH (o:OutboxEvent {aggregateId: $f}) RETURN count(o) AS n").bind(failure.toString()).to("f").fetch().one().orElseThrow()).containsEntry("n", 1L);
    }

    @Test
    void theScriptSaysClearlyWhenThereIsNothingToResendAndWhenItIsCalledWrong() {
        RedriveProcedure.copyScript(KAFKA);

        ExecResult nothing = RedriveProcedure.runScript(KAFKA, RedriveProcedure.TESTCONTAINERS_BOOTSTRAP, "quality.failure.DLT", "quality.failure", UUID.randomUUID().toString());
        ExecResult wrong = RedriveProcedure.runScript(KAFKA, "solo-un-argumento");

        assertThat(nothing.getExitCode()).isEqualTo(1);
        assertThat(nothing.getStderr()).contains("No hay mensajes con la clave");
        assertThat(wrong.getExitCode()).isEqualTo(2);
        assertThat(wrong.getStderr()).contains("Uso:");
    }
}
