package com.nodotextil.trazatex.apigateway.infrastructure.security;

import com.nodotextil.trazatex.apigateway.GatewayTestSupport;
import com.nodotextil.trazatex.apigateway.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {"trazatex.jwt.cache-ttl=PT1S", "trazatex.jwt.min-reload-interval=PT1S", "trazatex.jwt.failure-retry-interval=PT1S",
        "trazatex.jwt.read-timeout=PT0.5S", "test.case=jwks-unavailable"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class JwksUnavailableTest extends GatewayTestSupport {

    private int status(String token) {
        return anonymous.get().uri("/api/license").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                .returnResult(String.class).getStatus().value();
    }

    private void jwksReturns(int status) {
        JWKS.stubFor(get(urlEqualTo(JWKS_PATH)).willReturn(status == 200
                ? aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(TestTokens.jwks(TestTokens.KEY))
                : aResponse().withStatus(status)));
    }

    @Test
    void sinCacheEsUn503DelGatewayYSeRecuperaSolo() throws InterruptedException {
        ORG_ACCESS.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        String token = TestTokens.operator();

        jwksReturns(500);
        anonymous.get().uri("/api/license").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("AUTH_UNAVAILABLE")
                .jsonPath("$.message").value(m -> assertThat(m.toString()).doesNotContain("localhost", "jwks", "500"));

        ORG_ACCESS.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        anonymous.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isOk();

        jwksReturns(200);
        Thread.sleep(1_200);
        assertThat(status(token)).isEqualTo(200);
    }

    @Test
    void unJwksLentoTambienEs503() {
        ORG_ACCESS.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        JWKS.stubFor(get(urlEqualTo(JWKS_PATH)).willReturn(aResponse().withStatus(200).withFixedDelay(3_000).withBody(TestTokens.jwks(TestTokens.KEY))));
        anonymous.get().uri("/api/license").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.operator()).exchange()
                .expectStatus().isEqualTo(503).expectBody().jsonPath("$.code").isEqualTo("AUTH_UNAVAILABLE");
    }

    @Test
    void conLaCacheCargadaUnaCaidaDeOrganizationAccessNoTumbaALosUsuarios() throws InterruptedException {
        ORG_ACCESS.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        String token = TestTokens.operator();
        assertThat(status(token)).as("carga la caché").isEqualTo(200);

        jwksReturns(500);
        Thread.sleep(1_200);
        assertThat(status(token)).as("sirve con la caché vencida").isEqualTo(200);
        assertThat(status(token)).isEqualTo(200);
    }
}
