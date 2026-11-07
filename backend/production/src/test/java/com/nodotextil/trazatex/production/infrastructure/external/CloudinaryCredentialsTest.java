package com.nodotextil.trazatex.production.infrastructure.external;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryCredentialsTest {

    @Test
    void itParsesTheCloudinaryUrl() {
        CloudinaryCredentials credentials = CloudinaryCredentials.parse("cloudinary://123456:abc_DEF-ghi@mi-nube");

        assertThat(credentials.apiKey()).isEqualTo("123456");
        assertThat(credentials.apiSecret()).isEqualTo("abc_DEF-ghi");
        assertThat(credentials.cloudName()).isEqualTo("mi-nube");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankMeansNotConfigured(String url) {
        assertThat(CloudinaryCredentials.parse(url)).isNull();
        assertThat(CloudinaryCredentials.parse(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://123:secreto@nube", "cloudinary://solo-clave@nube", "cloudinary://123:secreto", "cloudinary://:secreto@nube",
            "cloudinary://123:sec reto@nube", "secreto-sin-formato"})
    void aMalformedUrlFailsWithoutRepeatingTheSecret(String url) {
        assertThatThrownBy(() -> CloudinaryCredentials.parse(url))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cloudinary://API_KEY:API_SECRET@CLOUD_NAME")
                .hasMessageNotContaining("secreto").hasMessageNotContaining("123");
    }

    @Test
    void neitherTheCredentialsNorThePropertiesPrintTheSecret() {
        CloudinaryCredentials credentials = CloudinaryCredentials.parse("cloudinary://123456:ultra-secreto@nube");
        CloudinaryProperties properties = new CloudinaryProperties("cloudinary://123456:ultra-secreto@nube", "https://api.cloudinary.com",
                java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(8), new CircuitBreakerSettings(10, 5, 50, java.time.Duration.ofSeconds(30), 2));

        assertThat(credentials.toString()).doesNotContain("ultra-secreto").contains("nube");
        assertThat(properties.toString()).doesNotContain("ultra-secreto").doesNotContain("123456");
    }
}
