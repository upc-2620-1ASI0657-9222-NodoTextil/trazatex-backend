package com.nodotextil.trazatex.sharedmessaging.producer;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReliableKafkaProducerTest {

    @Test
    void imponeAcksAllYProductorIdempotenteAunqueLaConfiguracionBaseDigaOtraCosa() {
        Map<String, Object> config = ReliableKafkaProducer.enforce(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                ProducerConfig.ACKS_CONFIG, "1",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false,
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 50));

        assertThat(config)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    }

    @Test
    void laFabricaCreadaLlevaLosAjustesObligatorios() {
        ProducerFactory<String, String> factory = ReliableKafkaProducer.producerFactory("localhost:9092");

        assertThat(factory.getConfigurationProperties())
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    }

    @Test
    void noModificaElMapaOriginal() {
        Map<String, Object> base = new java.util.HashMap<>(Map.of(ProducerConfig.ACKS_CONFIG, "1"));

        ReliableKafkaProducer.enforce(base);

        assertThat(base).containsEntry(ProducerConfig.ACKS_CONFIG, "1");
    }
}
