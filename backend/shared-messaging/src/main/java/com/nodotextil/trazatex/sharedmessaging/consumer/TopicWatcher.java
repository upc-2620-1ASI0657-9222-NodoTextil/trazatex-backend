package com.nodotextil.trazatex.sharedmessaging.consumer;

import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TopicWatcher {

    private static final Logger log = LoggerFactory.getLogger(TopicWatcher.class);

    public record Source(String listenerId, String topic) {
    }

    private final KafkaListenerEndpointRegistry registry;
    private final KafkaAdmin kafkaAdmin;
    private final List<Source> sources;

    public TopicWatcher(KafkaListenerEndpointRegistry registry, KafkaAdmin kafkaAdmin, List<Source> sources) {
        this.registry = registry;
        this.kafkaAdmin = kafkaAdmin;
        this.sources = List.copyOf(sources);
    }

    public void startConsumersWhoseTopicExists() {
        boolean anyStopped = sources.stream().anyMatch(source -> isStopped(registry.getListenerContainer(source.listenerId())));
        if (!anyStopped) {
            return;
        }
        Set<String> existing = existingTopics();
        for (Source source : sources) {
            MessageListenerContainer container = registry.getListenerContainer(source.listenerId());
            if (!isStopped(container)) {
                continue;
            }
            if (existing.contains(source.topic())) {
                log.info("El tópico {} ya existe: se empieza a consumir", source.topic());
                container.start();
            } else {
                log.debug("El tópico {} todavía no existe", source.topic());
            }
        }
    }

    private static boolean isStopped(MessageListenerContainer container) {
        return container != null && !container.isRunning();
    }

    private Set<String> existingTopics() {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            return admin.listTopics().names().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Set.of();
        } catch (Exception e) {
            log.warn("No se pudo consultar qué tópicos existen: {}", e.toString());
            return Set.of();
        }
    }
}
