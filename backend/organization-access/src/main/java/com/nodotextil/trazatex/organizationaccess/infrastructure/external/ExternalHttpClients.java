package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

public final class ExternalHttpClients {

    private ExternalHttpClients() {
    }

    /**
     * {@code RestClient} con el mismo timeout de conexión y de lectura. El cliente JDK no sigue redirecciones por defecto:
     * el {@code Authorization} nunca viaja a otro host.
     */
    public static RestClient restClient(String baseUrl, Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
