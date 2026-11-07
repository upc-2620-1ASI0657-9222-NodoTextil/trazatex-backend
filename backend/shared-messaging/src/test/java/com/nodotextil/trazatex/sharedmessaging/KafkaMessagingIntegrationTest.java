package com.nodotextil.trazatex.sharedmessaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.outbox.EventPublishException;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxMessage;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxRelay;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import com.nodotextil.trazatex.sharedkernel.testsupport.RecordingTransactionManager;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.sharedmessaging.consumer.InMemoryProcessedEventStore;
import com.nodotextil.trazatex.sharedmessaging.consumer.KafkaConsumerErrorHandling;
import com.nodotextil.trazatex.sharedmessaging.consumer.KafkaConsumerErrorHandling.RetrySettings;
import com.nodotextil.trazatex.sharedmessaging.consumer.NonRetryableEventException;
import com.nodotextil.trazatex.sharedmessaging.producer.KafkaEventPublisher;
import com.nodotextil.trazatex.sharedmessaging.producer.ReliableKafkaProducer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import com.nodotextil.trazatex.sharedtesting.TrazatexKafkaContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Testcontainers
class KafkaMessagingIntegrationTest {

    @Container
    static final KafkaContainer KAFKA = new TrazatexKafkaContainer();

    private static KafkaTemplate<String, String> template;
    private static KafkaEventPublisher publisher;

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final List<KafkaMessageListenerContainer<String, String>> containers = new ArrayList<>();

    @BeforeAll
    static void createProducer() {
        template = ReliableKafkaProducer.kafkaTemplate(KAFKA.getBootstrapServers());
        publisher = new KafkaEventPublisher(template, Duration.ofSeconds(15));
    }

    @AfterEach
    void stopContainers() {
        containers.forEach(KafkaMessageListenerContainer::stop);
    }

    @Test
    void publicarEsperaLaConfirmacionYLaClaveDelMensajeEsElAggregateId() {
        String topic = topic("production.batch");

        publisher.publish(topic, "b-1", "{\"n\":1}");

        List<ConsumerRecord<String, String>> records = consume(topic, 1);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).key()).isEqualTo("b-1");
        assertThat(records.get(0).value()).isEqualTo("{\"n\":1}");
    }

    @Test
    void losMensajesDeUnMismoAgregadoLlegaranEnElOrdenDePublicacion() {
        String topic = topic("production.batch");
        for (int i = 0; i < 30; i++) {
            publisher.publish(topic, "b-1", "m" + i);
        }

        List<String> values = consume(topic, 30).stream().map(ConsumerRecord::value).toList();

        assertThat(values).containsExactlyElementsOf(java.util.stream.IntStream.range(0, 30).mapToObj(i -> "m" + i).toList());
    }

    @Test
    void agotadosLosReintentosElMensajeVaALaDltConSuClave() {
        String topic = topic("production.batch");
        AtomicInteger attempts = new AtomicInteger();
        startListener(topic, record -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("el caso de uso falla siempre");
        }, new RetrySettings(2, Duration.ofMillis(50), 2.0, Duration.ofMillis(200)));

        publisher.publish(topic, "b-1", "{\"n\":1}");

        List<ConsumerRecord<String, String>> dlt = consume(topic + ".DLT", 1);
        assertThat(attempts).hasValue(3); // intento inicial + 2 reintentos
        assertThat(dlt.get(0).key()).isEqualTo("b-1");
        assertThat(dlt.get(0).value()).isEqualTo("{\"n\":1}");
    }

    @Test
    void unSobreMalformadoVaDirectoALaDltSinReintentos() {
        String topic = topic("production.batch");
        AtomicInteger attempts = new AtomicInteger();
        IdempotentEventHandler handler = new IdempotentEventHandler(new InMemoryProcessedEventStore(),
                new RecordingTransactionManager(), mapper);
        startListener(topic, record -> {
            attempts.incrementAndGet();
            handler.handle(record.value(), event -> { });
        }, new RetrySettings(3, Duration.ofMillis(50), 2.0, Duration.ofMillis(200)));

        publisher.publish(topic, "b-1", "esto no es un sobre");

        List<ConsumerRecord<String, String>> dlt = consume(topic + ".DLT", 1);
        assertThat(dlt.get(0).value()).isEqualTo("esto no es un sobre");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void unEventoQueNoSePuedeAplicarVaDirectoALaDltSinReintentosPeroUnFalloTransitorioSiSeReintenta() {
        String topic = topic("production.batch");
        AtomicInteger permanentAttempts = new AtomicInteger();
        AtomicInteger transientAttempts = new AtomicInteger();
        startListener(topic, record -> {
            if (record.key().equals("sin-lote")) {
                permanentAttempts.incrementAndGet();
                throw new NonRetryableEventException("el lote no existe");
            }
            if (transientAttempts.incrementAndGet() < 3) {
                throw new IllegalStateException("base de datos no disponible");
            }
        }, new RetrySettings(3, Duration.ofMillis(50), 2.0, Duration.ofMillis(200)));

        publisher.publish(topic, "sin-lote", "{\"n\":1}");
        publisher.publish(topic, "transitorio", "{\"n\":2}");

        List<ConsumerRecord<String, String>> dlt = consume(topic + ".DLT", 1);
        assertThat(dlt.get(0).key()).isEqualTo("sin-lote");
        assertThat(permanentAttempts).hasValue(1);
        await().atMost(Duration.ofSeconds(10)).until(() -> transientAttempts.get() == 3);
    }

    @Test
    void relayKafkaYConsumidorIdempotenteDeExtremoAExtremoConEntregaDuplicada() throws Exception {
        String topic = topic("production.batch");
        BaseDomainEvent registered = BaseDomainEvent.of("BatchRegistered", "b-1", Map.of("step", 1));
        BaseDomainEvent phase = BaseDomainEvent.of("BatchPhaseChanged", "b-1", Map.of("step", 2));
        BaseDomainEvent split = BaseDomainEvent.of("BatchSplit", "b-1", Map.of("step", 3));

        ListStore store = new ListStore();
        Instant t0 = Instant.parse("2026-10-19T14:00:00Z");
        store.add(outbox(topic, split, t0.plusSeconds(2)));
        store.add(outbox(topic, registered, t0));
        store.add(outbox(topic, phase, t0.plusSeconds(1)));
        OutboxRelay relay = new OutboxRelay(store, publisher, new RecordingTransactionManager());

        IdempotentEventHandler handler = new IdempotentEventHandler(new InMemoryProcessedEventStore(),
                new RecordingTransactionManager(), mapper);
        List<String> effects = new CopyOnWriteArrayList<>();
        AtomicInteger deliveries = new AtomicInteger();
        startListener(topic, record -> {
            deliveries.incrementAndGet();
            handler.handle(record.value(), event -> effects.add(event.type()));
        }, RetrySettings.DEFAULT);

        OutboxRelay.Result result = relay.publishPending();
        publisher.publish(topic, "b-1", mapper.writeValueAsString(phase)); // reentrega at-least-once

        assertThat(result).isEqualTo(new OutboxRelay.Result(3, 0, 0));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(deliveries).hasValue(4));
        assertThat(effects).containsExactly("BatchRegistered", "BatchPhaseChanged", "BatchSplit"); // en orden y sin duplicado
        assertThat(store.pendingCount()).isZero();
    }

    @Test
    void publicarEnUnTopicoQueNadieDeclaroFallaYElTopicoNoSeCrea() throws Exception {
        String undeclared = "production.batch." + UUID.randomUUID();
        KafkaTemplate<String, String> impatient = new KafkaTemplate<>(ReliableKafkaProducer.producerFactory(
                Map.of("bootstrap.servers", KAFKA.getBootstrapServers(), "max.block.ms", 2000)));
        KafkaEventPublisher impatientPublisher = new KafkaEventPublisher(impatient, Duration.ofSeconds(5));

        assertThatThrownBy(() -> impatientPublisher.publish(undeclared, "b-1", "{\"n\":1}")).isInstanceOf(EventPublishException.class);

        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            assertThat(admin.listTopics().names().get(10, TimeUnit.SECONDS)).doesNotContain(undeclared);
        }
    }

    // --- Ayudas ---------------------------------------------------------------------------------

    /**
     * Un tópico nuevo, declarado como lo haría un servicio: el productor declara el tópico y el consumidor su {@code .DLT} (el broker
     * de las pruebas no crea tópicos solos, como el del compose; ver {@code docs/conventions.md}).
     */
    private static String topic(String base) {
        String name = base + "." + UUID.randomUUID();
        declare(name, name + KafkaConsumerErrorHandling.DLT_SUFFIX);
        return name;
    }

    /** Lo que hace {@code KafkaAdmin} con los {@code NewTopic} de un servicio: 1 partición y 1 réplica. */
    private static void declare(String... names) {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(Arrays.stream(names).map(name -> new NewTopic(name, 1, (short) 1)).toList()).all().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo declarar " + Arrays.toString(names), e);
        }
    }

    private OutboxMessage outbox(String topic, BaseDomainEvent event, Instant createdAt) throws Exception {
        return new OutboxMessage(event.eventId(), "Batch", event.aggregateId(), event.type(), topic,
                mapper.writeValueAsString(event), createdAt);
    }

    private void startListener(String topic, MessageListener<String, String> listener, RetrySettings retry) {
        ContainerProperties properties = new ContainerProperties(topic);
        properties.setGroupId("it-" + UUID.randomUUID());
        properties.setMessageListener(listener);
        KafkaMessageListenerContainer<String, String> container = new KafkaMessageListenerContainer<>(
                new DefaultKafkaConsumerFactory<>(consumerConfig(properties.getGroupId())), properties);
        container.setCommonErrorHandler(KafkaConsumerErrorHandling.deadLetterErrorHandler(template, retry));
        container.start();
        containers.add(container);
    }

    private List<ConsumerRecord<String, String>> consume(String topic, int expected) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerConfig("it-" + UUID.randomUUID()))) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(60)).until(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
                return records.size() >= expected;
            });
        }
        return records;
    }

    private static Map<String, Object> consumerConfig(String groupId) {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    }

    /** Almacén mínimo del outbox para el recorrido completo (sin base de datos). */
    private static final class ListStore implements OutboxStorePort {
        private final List<OutboxMessage> pending = new ArrayList<>();

        void add(OutboxMessage message) {
            pending.add(message);
        }

        long pendingCount() {
            return pending.size();
        }

        @Override
        public void save(OutboxMessage message) {
            pending.add(message);
        }

        @Override
        public List<OutboxMessage> findPending(int limit) {
            return pending.stream().limit(limit).toList();
        }

        @Override
        public void markPublished(UUID id, Instant publishedAt) {
            pending.removeIf(message -> message.id().equals(id));
        }

        @Override
        public void markFailed(UUID id, String error) {
        }
    }
}
