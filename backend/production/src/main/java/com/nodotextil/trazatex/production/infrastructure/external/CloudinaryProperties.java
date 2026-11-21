package com.nodotextil.trazatex.production.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

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
