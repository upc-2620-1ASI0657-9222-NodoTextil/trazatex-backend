package com.nodotextil.trazatex.sharedmessaging.producer;

import com.nodotextil.trazatex.sharedkernel.outbox.EventPublishException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaEventPublisherTest {

    /** Plantilla que no toca la red: cada envío devuelve el futuro que decida la prueba. */
    private static final class ScriptedTemplate extends KafkaTemplate<String, String> {
        final List<String> sent = new ArrayList<>();
        Supplier<CompletableFuture<SendResult<String, String>>> outcome;

        ScriptedTemplate() {
            super(ReliableKafkaProducer.producerFactory("localhost:1"));
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(String topic, String key, String data) {
            sent.add(topic + "|" + key + "|" + data);
            return outcome.get();
        }
    }

    @Test
    void retornaSoloCuandoElBrokerConfirmaYEnviaTopicoClaveYPayload() throws Exception {
        ScriptedTemplate template = new ScriptedTemplate();
        CompletableFuture<SendResult<String, String>> confirmation = new CompletableFuture<>();
        template.outcome = () -> confirmation;
        KafkaEventPublisher publisher = new KafkaEventPublisher(template, Duration.ofSeconds(5));
        CompletableFuture<Void> call = CompletableFuture.runAsync(() -> publisher.publish("production.batch", "b-1", "{}"));

        Thread.sleep(150);
        assertThat(call).isNotDone(); // sin confirmación, no retorna

        confirmation.complete(null);
        call.get();

        assertThat(template.sent).containsExactly("production.batch|b-1|{}");
    }

    @Test
    void siKafkaRechazaElEnvioLanzaEventPublishException() {
        ScriptedTemplate template = new ScriptedTemplate();
        template.outcome = () -> CompletableFuture.failedFuture(new IllegalStateException("NotEnoughReplicas"));

        assertThatThrownBy(() -> new KafkaEventPublisher(template).publish("t", "k", "v"))
                .isInstanceOf(EventPublishException.class)
                .hasRootCauseMessage("NotEnoughReplicas");
    }

    @Test
    void siElEnvioFallaSincronicamenteSinDevolverUnFuturoLanzaEventPublishException() {
        ScriptedTemplate template = new ScriptedTemplate();
        template.outcome = () -> {
            throw new org.springframework.kafka.KafkaException("Send failed",
                    new org.apache.kafka.common.errors.TimeoutException("Topic t not present in metadata after 2000 ms."));
        };

        assertThatThrownBy(() -> new KafkaEventPublisher(template).publish("t", "k", "v"))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("t").hasRootCauseMessage("Topic t not present in metadata after 2000 ms.");
    }

    @Test
    void siNoHayConfirmacionATiempoLanzaEventPublishException() {
        ScriptedTemplate template = new ScriptedTemplate();
        template.outcome = CompletableFuture::new;

        assertThatThrownBy(() -> new KafkaEventPublisher(template, Duration.ofMillis(100)).publish("t", "k", "v"))
                .isInstanceOf(EventPublishException.class);
    }

    @Test
    void siSeInterrumpeElHiloConservaLaInterrupcion() {
        ScriptedTemplate template = new ScriptedTemplate();
        template.outcome = CompletableFuture::new;
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> new KafkaEventPublisher(template).publish("t", "k", "v"))
                    .isInstanceOf(EventPublishException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
