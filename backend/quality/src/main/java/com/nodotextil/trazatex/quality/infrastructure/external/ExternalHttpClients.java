package com.nodotextil.trazatex.quality.infrastructure.external;

import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

public final class ExternalHttpClients {

    private ExternalHttpClients() {
    }

    /** {@code RestClient} con timeouts de conexión y de lectura. El cliente JDK no sigue redirecciones: las credenciales nunca viajan a otro host. */
    public static RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        // HTTP/1.1 explícito: sin él el cliente JDK intenta mejorar a h2c en conexiones sin TLS y un servidor intermedio puede fallar con cuerpos grandes.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(connectTimeout).build());
        factory.setReadTimeout(readTimeout);
        // En memoria: la petición lleva Content-Length (no "chunked"), que es lo que esperan las APIs de subida
        return RestClient.builder().baseUrl(baseUrl).requestFactory(new BufferingClientHttpRequestFactory(factory)).build();
    }
}
