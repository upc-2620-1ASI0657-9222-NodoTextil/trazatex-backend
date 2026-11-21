package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public final class ChequeaStubs {

    public static final String RUC = "20131312955";

    private ChequeaStubs() {
    }

    public static String file(String name) {
        try (InputStream in = ChequeaStubs.class.getResourceAsStream("/chequea/" + name)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String required(String name) {
        String content = file(name);
        if (content == null) {
            throw new IllegalStateException("Falta el archivo de prueba chequea/" + name);
        }
        return content;
    }

    public static MappingBuilder forRuc(String ruc) {
        return get(urlEqualTo("/api/v1/ruc/" + ruc));
    }

    public static ResponseDefinitionBuilder json(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
    }

    public static ResponseDefinitionBuilder problem(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/problem+json").withBody(body);
    }

    public static ResponseDefinitionBuilder notFound() {
        String real = file("ruc-no-existe.json");
        return real == null ? aResponse().withStatus(404) : problem(404, real);
    }
}
