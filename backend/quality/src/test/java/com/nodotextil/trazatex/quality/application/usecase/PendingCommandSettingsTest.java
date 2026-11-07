package com.nodotextil.trazatex.quality.application.usecase;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingCommandSettingsTest {

    private final PendingCommandSettings settings = new PendingCommandSettings(Duration.ofSeconds(30), 10, Duration.ofMinutes(2), Duration.ofSeconds(15), 2.0,
            Duration.ofMinutes(5), 20, Duration.ofMinutes(5));

    @Test
    void theWaitGrowsByTheMultiplierAndStopsAtTheCapWithoutALimitOnAttempts() {
        assertThat(settings.delayAfter(1)).isEqualTo(Duration.ofSeconds(15));
        assertThat(settings.delayAfter(2)).isEqualTo(Duration.ofSeconds(30));
        assertThat(settings.delayAfter(3)).isEqualTo(Duration.ofSeconds(60));
        assertThat(settings.delayAfter(4)).isEqualTo(Duration.ofSeconds(120));
        assertThat(settings.delayAfter(5)).isEqualTo(Duration.ofSeconds(240));
        assertThat(settings.delayAfter(6)).isEqualTo(Duration.ofMinutes(5));
        assertThat(settings.delayAfter(50)).isEqualTo(Duration.ofMinutes(5));
        assertThat(settings.delayAfter(100_000)).as("sin desbordar").isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void invalidSettingsDoNotStart() {
        assertThatThrownBy(() -> new PendingCommandSettings(Duration.ZERO, 0, Duration.ofMinutes(1), Duration.ofSeconds(1), 2.0, Duration.ofMinutes(5), 20,
                Duration.ofMinutes(5))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PendingCommandSettings(Duration.ZERO, 1, Duration.ofMinutes(1), Duration.ofSeconds(1), 0.5, Duration.ofMinutes(5), 20,
                Duration.ofMinutes(5))).isInstanceOf(IllegalArgumentException.class);
    }
}
