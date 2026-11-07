package com.nodotextil.trazatex.sharedmessaging.consumer;

import com.nodotextil.trazatex.sharedtesting.TrazatexKafkaContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * El vigilante contra un broker real y {@code @KafkaListener(autoStartup = "false")} como en un servicio: los contenedores solo
 * arrancan cuando su tópico existe.
 */
@Testcontainers
class TopicWatcherIntegrationTest {

    private static final String FIRST = "watcher-first";
    private static final String SECOND = "watcher-second";

    @Container
    static final KafkaContainer KAFKA = new TrazatexKafkaContainer();

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void unTopicoAusenteNoArrancaSuContenedorYCuandoApareceSiArranca() {
        String topic = topic("quality.control");
        TopicWatcher watcher = start(topic, topic("unused"), new AtomicInteger(), new TopicWatcher.Source(FIRST, topic));

        watcher.startConsumersWhoseTopicExists();
        assertThat(running(FIRST)).isFalse();

        createTopic(topic);
        watcher.startConsumersWhoseTopicExists();
        await().untilAsserted(() -> assertThat(running(FIRST)).isTrue());
    }

    @Test
    void soloArrancanLosContenedoresDeLosTopicosQueYaExisten() {
        String existing = topic("quality.control");
        String missing = topic("quality.failure");
        createTopic(existing);
        TopicWatcher watcher = start(existing, missing, new AtomicInteger(),
                new TopicWatcher.Source(FIRST, existing), new TopicWatcher.Source(SECOND, missing));

        watcher.startConsumersWhoseTopicExists();

        await().untilAsserted(() -> assertThat(running(FIRST)).isTrue());
        assertThat(running(SECOND)).isFalse();
    }

    @Test
    void conTodosLosContenedoresEnMarchaNoConsultaAlBroker() {
        String topic = topic("quality.control");
        createTopic(topic);
        AtomicInteger brokerQueries = new AtomicInteger();
        TopicWatcher watcher = start(topic, topic("unused"), brokerQueries, new TopicWatcher.Source(FIRST, topic));
        watcher.startConsumersWhoseTopicExists();
        await().untilAsserted(() -> assertThat(running(FIRST)).isTrue());
        int queriesToStart = brokerQueries.get();

        watcher.startConsumersWhoseTopicExists();
        watcher.startConsumersWhoseTopicExists();

        assertThat(queriesToStart).isPositive();
        assertThat(brokerQueries).hasValue(queriesToStart);
    }

    @Test
    void unaFuenteSinContenedorRegistradoSeIgnora() {
        String topic = topic("quality.control");
        createTopic(topic);
        TopicWatcher watcher = start(topic, topic("unused"), new AtomicInteger(),
                new TopicWatcher.Source("no-existe-" + UUID.randomUUID(), topic), new TopicWatcher.Source(FIRST, topic));

        watcher.startConsumersWhoseTopicExists();

        await().untilAsserted(() -> assertThat(running(FIRST)).isTrue());
    }

    // ---- ayudas

    private static String topic(String base) {
        return base + "." + UUID.randomUUID();
    }

    private boolean running(String listenerId) {
        return context.getBean(KafkaListenerEndpointRegistry.class).getListenerContainer(listenerId).isRunning();
    }

    /** Levanta un contexto con dos {@code @KafkaListener(autoStartup=false)} (uno por tópico) y devuelve el vigilante sobre las fuentes dadas. */
    private TopicWatcher start(String firstTopic, String secondTopic, AtomicInteger brokerQueries, TopicWatcher.Source... sources) {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("topics",
                Map.of("watcher.first.topic", firstTopic, "watcher.second.topic", secondTopic)));
        context.registerBean("kafkaListenerContainerFactory", ConcurrentKafkaListenerContainerFactory.class, TopicWatcherIntegrationTest::containerFactory);
        context.register(ListenersConfig.class);
        context.refresh();
        KafkaAdmin admin = new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers())) {
            @Override
            public Map<String, Object> getConfigurationProperties() {
                brokerQueries.incrementAndGet();
                return super.getConfigurationProperties();
            }
        };
        return new TopicWatcher(context.getBean(KafkaListenerEndpointRegistry.class), admin, List.of(sources));
    }

    private static ConcurrentKafkaListenerContainerFactory<String, String> containerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false)));
        return factory;
    }

    private static void createTopic(String topic) {
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @org.springframework.context.annotation.Configuration
    @EnableKafka
    static class ListenersConfig {

        @org.springframework.context.annotation.Bean
        Listeners listeners() {
            return new Listeners();
        }
    }

    static class Listeners {

        @KafkaListener(id = FIRST, topics = "${watcher.first.topic}", autoStartup = "false")
        void first(String ignored) {
        }

        @KafkaListener(id = SECOND, topics = "${watcher.second.topic}", autoStartup = "false")
        void second(String ignored) {
        }
    }
}
