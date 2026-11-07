package com.nodotextil.trazatex.organizationaccess.infrastructure.config;

import com.nodotextil.trazatex.organizationaccess.infrastructure.messaging.Topics;
import com.nodotextil.trazatex.sharedkernel.outbox.EventPublisherPort;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxRelay;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import com.nodotextil.trazatex.sharedmessaging.producer.KafkaEventPublisher;
import com.nodotextil.trazatex.sharedmessaging.producer.ReliableKafkaProducer;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.Map;

/**
 * Publicación de los eventos (ADR-004): el relay lee el outbox y publica en Kafka <b>después</b> del commit, y los tópicos que
 * el servicio produce se declaran aquí (el broker tiene {@code auto.create.topics.enable=false}, ver {@code conventions.md}).
 * {@code trazatex.messaging.enabled=false} apaga todo esto (pruebas que no necesitan broker).
 */
@Configuration
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class MessagingConfig {

    @Bean
    KafkaAdmin kafkaAdmin(@Value("${trazatex.messaging.bootstrap-servers}") String bootstrapServers,
                          @Value("${trazatex.messaging.modify-topic-configs:true}") boolean modifyTopicConfigs) {
        KafkaAdmin admin = new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        // Un NewTopic NO cambia la configuración de un tópico que ya existe, salvo con esto. Sirve en Kafka local, donde
        // orgaccess.user pudo crearse sin compactación y orgaccess.company con la retención por defecto; en Azure Event Hubs no se debe depender de ello (ver userTopic)
        admin.setModifyTopicConfigs(modifyTopicConfigs);
        // Sin broker al arrancar el servicio sí arranca: el outbox guarda los eventos y el relay reintenta (at-least-once)
        admin.setFatalIfBrokerNotAvailable(false);
        admin.setOperationTimeout(10);
        return admin;
    }

    /**
     * {@code retention.ms=-1}: <b>sin borrado por tiempo</b>. Un Production (o un Traceability) que arranca por primera vez, o se
     * reconstruye, lee el tópico desde el inicio para conocer las empresas ya registradas ({@code CompanyRegistered}) y su
     * privacidad ({@code CompanyPrivacyUpdated}); con la retención por defecto (7 días) esas altas antiguas desaparecerían. No
     * se puede compactar: tres tipos de evento comparten la clave {@code companyId} y la compactación dejaría solo el último.
     *
     * <p>Azure Event Hubs Standard retiene como máximo 7 días y su retención se fija al crear el event hub: allí esto no se
     * cumple (ver docs/progress.md, riesgo de reemisión de altas).
     */
    @Bean
    NewTopic companyTopic(@Value("${trazatex.messaging.partitions:1}") int partitions, @Value("${trazatex.messaging.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.COMPANY).partitions(partitions).replicas(replicas)
                .config(TopicConfig.RETENTION_MS_CONFIG, "-1").build();
    }

    @Bean
    NewTopic invitationTopic(@Value("${trazatex.messaging.partitions:1}") int partitions, @Value("${trazatex.messaging.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.INVITATION).partitions(partitions).replicas(replicas).build();
    }

    /**
     * {@code cleanup.policy=compact} (RF-012): la clave es el userId y {@code UserAccountUpdated} trae el estado completo, así que
     * Kafka conserva el último estado de cada usuario sin límite de tiempo. El Gateway relee el tópico desde el inicio al
     * arrancar para reconstruir el conjunto de usuarios {@code INACTIVE}; con {@code delete} (retención por tiempo) un usuario
     * inactivado hace tiempo se olvidaría y su token volvería a servir tras un reinicio.
     *
     * <p>En Azure Event Hubs (Standard) la compactación debe estar en el event hub <b>desde su creación</b>: no se puede cambiar
     * después (límite de 1 GB por partición); allí {@code trazatex.messaging.modify-topic-configs=false}.
     */
    @Bean
    NewTopic userTopic(@Value("${trazatex.messaging.partitions:1}") int partitions, @Value("${trazatex.messaging.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.USER).partitions(partitions).replicas(replicas).compact().build();
    }

    @Bean
    KafkaTemplate<String, String> outboxKafkaTemplate(@Value("${trazatex.messaging.bootstrap-servers}") String bootstrapServers) {
        return ReliableKafkaProducer.kafkaTemplate(bootstrapServers);
    }

    @Bean
    EventPublisherPort kafkaEventPublisher(KafkaTemplate<String, String> outboxKafkaTemplate) {
        return new KafkaEventPublisher(outboxKafkaTemplate);
    }

    @Bean
    OutboxRelay outboxRelay(OutboxStorePort store, EventPublisherPort publisher, PlatformTransactionManager transactionManager,
                            Clock clock, @Value("${trazatex.messaging.relay-batch-size:100}") int batchSize) {
        return new OutboxRelay(store, publisher, transactionManager, clock, batchSize);
    }
}
