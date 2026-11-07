package com.nodotextil.trazatex.production.infrastructure.config;

import com.nodotextil.trazatex.production.infrastructure.external.CircuitBreakerSettings;
import com.nodotextil.trazatex.production.infrastructure.external.CloudinaryProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceConfigTest {

    private final EvidenceConfig config = new EvidenceConfig();

    private CloudinaryProperties properties(String url) {
        return new CloudinaryProperties(url, "https://api.cloudinary.com", Duration.ofSeconds(2), Duration.ofSeconds(8),
                new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 2));
    }

    @Test
    void aMalformedCloudinaryUrlStopsStartupWithoutPrintingTheSecret() {
        assertThatThrownBy(() -> config.evidenceStorage(properties("cloudinary://123456:super-secreto-que-no-debe-salir"),
                CircuitBreakerRegistry.ofDefaults(), JsonMapper.builder().build(), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOUDINARY_URL")
                .hasMessageNotContaining("super-secreto").hasMessageNotContaining("123456");
    }

    @Test
    void anEmptyUrlStartsAnAdapterThatReportsItselfUnavailableInsteadOfFailing() {
        var storage = config.evidenceStorage(properties(""), CircuitBreakerRegistry.ofDefaults(), JsonMapper.builder().build(), Clock.systemUTC());

        assertThat(storage).isNotNull();
        assertThatThrownBy(() -> storage.upload("id", "id.jpg", "image/jpeg", new byte[]{1}))
                .isInstanceOf(com.nodotextil.trazatex.production.application.exception.StorageNotAvailableException.class)
                .hasMessageContaining("no está configurado");
    }
}
