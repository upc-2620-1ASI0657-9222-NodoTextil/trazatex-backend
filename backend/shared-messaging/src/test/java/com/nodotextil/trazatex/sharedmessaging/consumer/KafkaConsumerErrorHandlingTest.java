package com.nodotextil.trazatex.sharedmessaging.consumer;

import com.nodotextil.trazatex.sharedmessaging.consumer.KafkaConsumerErrorHandling.RetrySettings;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaConsumerErrorHandlingTest {

    @Test
    void laEsperaCrecePorUnMultiplicadorHastaElMaximoYLuegoSeAgotan() {
        BackOff backOff = KafkaConsumerErrorHandling.backOff(
                new RetrySettings(5, Duration.ofMillis(100), 2.0, Duration.ofMillis(300)));

        BackOffExecution execution = backOff.start();
        List<Long> waits = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            waits.add(execution.nextBackOff());
        }

        assertThat(waits).containsExactly(100L, 200L, 300L, 300L, 300L, BackOffExecution.STOP);
    }

    @Test
    void sinReintentosElPrimerFalloVaDirectoALaDlt() {
        BackOffExecution execution = KafkaConsumerErrorHandling.backOff(
                new RetrySettings(0, Duration.ofMillis(100), 2.0, Duration.ofMillis(300))).start();

        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void laDltEsElTopicoOriginalMasSufijo() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("production.batch", 2, 17L, "b-1", "{}");

        assertThat(KafkaConsumerErrorHandling.deadLetterTopic(record)).isEqualTo("production.batch.DLT");
    }

    @Test
    void laConfiguracionPorDefectoEsDe5ReintentosCrecientes() {
        assertThat(RetrySettings.DEFAULT.maxRetries()).isEqualTo(5);
        assertThat(RetrySettings.DEFAULT.initialInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(RetrySettings.DEFAULT.multiplier()).isEqualTo(2.0);
    }

    @Test
    void rechazaConfiguracionesInvalidas() {
        assertThatThrownBy(() -> new RetrySettings(-1, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetrySettings(3, Duration.ofSeconds(1), 0.5, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetrySettings(3, Duration.ofSeconds(10), 2.0, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void losDiezReintentosDeUnaFamiliaIncompletaVanDeUnSegundoASesentaYSumanCincoMinutos() {
        // Reintentos propios del análisis de impacto de Traceability (docs/domain/traceability.md): 10, de 1 s hasta 60 s
        BackOffExecution execution = KafkaConsumerErrorHandling.backOff(new RetrySettings(10, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(60))).start();
        List<Long> waits = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            waits.add(execution.nextBackOff());
        }

        assertThat(waits).containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L, 60_000L, 60_000L, BackOffExecution.STOP);
        assertThat(waits.stream().filter(wait -> wait != BackOffExecution.STOP).mapToLong(Long::longValue).sum()).as("≈ 5 minutos en total").isEqualTo(303_000L);
    }
}
