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

@Configuration
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class MessagingConfig {

    @Bean
    KafkaAdmin kafkaAdmin(@Value("${trazatex.messaging.bootstrap-servers}") String bootstrapServers,
                          @Value("${trazatex.messaging.modify-topic-configs:true}") boolean modifyTopicConfigs) {
        KafkaAdmin admin = new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        admin.setModifyTopicConfigs(modifyTopicConfigs);
        admin.setFatalIfBrokerNotAvailable(false);
        admin.setOperationTimeout(10);
        return admin;
    }

    @Bean
    NewTopic companyTopic(@Value("${trazatex.messaging.partitions:1}") int partitions, @Value("${trazatex.messaging.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.COMPANY).partitions(partitions).replicas(replicas)
                .config(TopicConfig.RETENTION_MS_CONFIG, "-1").build();
    }

    @Bean
    NewTopic invitationTopic(@Value("${trazatex.messaging.partitions:1}") int partitions, @Value("${trazatex.messaging.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.INVITATION).partitions(partitions).replicas(replicas).build();
    }

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
