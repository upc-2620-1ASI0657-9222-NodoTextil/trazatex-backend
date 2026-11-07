package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Respuestas de Chequea.pe para WireMock. Salen de los archivos de {@code src/test/resources/chequea/}: los dos reales
 * ({@code ruc-activo-habido.json}, {@code ruc-formato-invalido.json}) y las variantes {@code ruc-baja.json} y
 * {@code ruc-no-habido.json}, que son el real con solo {@code estado} o {@code condicion} cambiados.
 * Las pruebas nunca llaman a la API real.
 */
public final class ChequeaStubs {

    /** El RUC del archivo real {@code ruc-activo-habido.json} (SUNAT). */
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

    /**
     * 404 de "RUC no existe". Si existe {@code chequea/ruc-no-existe.json} (respuesta real) se usa; si no, el cuerpo va vacío:
     * el adaptador decide solo por el código de estado.
     */
    public static ResponseDefinitionBuilder notFound() {
        String real = file("ruc-no-existe.json");
        return real == null ? aResponse().withStatus(404) : problem(404, real);
    }
}
