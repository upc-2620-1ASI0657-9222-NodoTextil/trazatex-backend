package com.nodotextil.trazatex.quality.infrastructure.external;

import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

public final class ExternalHttpClients {

    private ExternalHttpClients() {
    }

    public static RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(connectTimeout).build());
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(new BufferingClientHttpRequestFactory(factory)).build();
    }
}
