package com.nodotextil.trazatex.quality.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cloudinary ({@code trazatex.cloudinary.*}). {@code url} es {@code CLOUDINARY_URL}
 * ({@code cloudinary://API_KEY:API_SECRET@CLOUD_NAME}), un secreto: solo por variable de entorno y nunca en un log. Vacía =
 * sin configurar (todo va a la cola). {@code baseUrl} es la API de subida; las pruebas la apuntan a WireMock.
 */
@ConfigurationProperties("trazatex.cloudinary")
public record CloudinaryProperties(
        @DefaultValue("") String url,
        @DefaultValue("https://api.cloudinary.com") String baseUrl,
        @DefaultValue("PT2S") Duration connectTimeout,
        @DefaultValue("PT8S") Duration readTimeout,
        @DefaultValue CircuitBreakerSettings circuitBreaker) {

    @Override
    public String toString() {
        return "CloudinaryProperties[url=" + (url == null || url.isBlank() ? "(sin configurar)" : "(configurada)") + ", baseUrl=" + baseUrl
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
    }
}
