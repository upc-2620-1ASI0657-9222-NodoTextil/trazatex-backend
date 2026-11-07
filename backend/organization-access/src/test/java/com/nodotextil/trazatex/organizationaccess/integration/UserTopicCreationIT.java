package com.nodotextil.trazatex.organizationaccess.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import com.nodotextil.trazatex.sharedtesting.TrazatexKafkaContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caso de Azure Event Hubs: el tópico se crea <b>con</b> compactación desde el principio y el servicio no depende de modificar
 * configuraciones de tópicos existentes ({@code modify-topic-configs=false}).
 */
@SpringBootTest(properties = {"trazatex.messaging.enabled=true", "trazatex.messaging.modify-topic-configs=false"})
class UserTopicCreationIT extends IntegrationTestBase {

    static final KafkaContainer KAFKA = new TrazatexKafkaContainer();

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("trazatex.messaging.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Test
    void aNewUserTopicIsCreatedCompactedWithoutTouchingExistingTopicConfigs() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            var configs = admin.describeConfigs(List.of(
                    new ConfigResource(ConfigResource.Type.TOPIC, "orgaccess.user"),
                    new ConfigResource(ConfigResource.Type.TOPIC, "orgaccess.company"))).all().get();
            assertThat(configs.get(new ConfigResource(ConfigResource.Type.TOPIC, "orgaccess.user")).get("cleanup.policy").value()).isEqualTo("compact");
            assertThat(configs.get(new ConfigResource(ConfigResource.Type.TOPIC, "orgaccess.company")).get("cleanup.policy").value()).isEqualTo("delete");
        }
    }

    @Test
    void aNewCompanyTopicIsCreatedWithoutTimeRetentionAndTheInvitationTopicKeepsTheDefault() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            var configs = admin.describeConfigs(List.of(
                    new ConfigResource(ConfigResource.Type.TOPIC, "orgaccess.company"),
                    new ConfigResource(ConfigResource.Type.TOPIC, "orgaccess.invitation"))).all().get();
            assertThat(configs.get(new ConfigResource(ConfigResource.Type.TOPIC, "orgaccess.company")).get("retention.ms").value()).isEqualTo("-1");
            assertThat(configs.get(new ConfigResource(ConfigResource.Type.TOPIC, "orgaccess.invitation")).get("retention.ms").value())
                    .isNotEqualTo("-1");
        }
    }
}
