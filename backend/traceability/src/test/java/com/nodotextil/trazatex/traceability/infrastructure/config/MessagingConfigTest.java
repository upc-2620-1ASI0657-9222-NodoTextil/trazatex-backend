package com.nodotextil.trazatex.traceability.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La proporción entre la espera más larga de los reintentos y {@code max.poll.interval.ms}: una espera es una quinta parte, y romper la mitad impide arrancar. */
class MessagingConfigTest {

    @Test
    void theDefaultsKeepTheLongestWaitAtAFifthOfTheMaxPollInterval() {
        assertThatCode(() -> MessagingConfig.requireWaitsThatFitInThePollInterval(Duration.ofSeconds(60), Duration.ofMinutes(5))).doesNotThrowAnyException();
        assertThat(Duration.ofMinutes(5).dividedBy(Duration.ofSeconds(60))).isEqualTo(5);
    }

    @Test
    void theScaledValuesOfTheKafkaTestsKeepTheSameProportion() {
        assertThatCode(() -> MessagingConfig.requireWaitsThatFitInThePollInterval(Duration.ofSeconds(2), Duration.ofSeconds(10))).doesNotThrowAnyException();
        assertThat(Duration.ofSeconds(10).dividedBy(Duration.ofSeconds(2))).isEqualTo(5);
    }

    @Test
    void exactlyHalfIsAcceptedAndMoreThanHalfIsNot() {
        assertThatCode(() -> MessagingConfig.requireWaitsThatFitInThePollInterval(Duration.ofSeconds(150), Duration.ofMinutes(5))).doesNotThrowAnyException();
        assertThatThrownBy(() -> MessagingConfig.requireWaitsThatFitInThePollInterval(Duration.ofSeconds(151), Duration.ofMinutes(5)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("max.poll.interval.ms").hasMessageContaining("rebalanceo");
        assertThatThrownBy(() -> MessagingConfig.requireWaitsThatFitInThePollInterval(Duration.ofMinutes(10), Duration.ofMinutes(5))).isInstanceOf(IllegalStateException.class);
    }
}
