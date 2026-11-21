package com.nodotextil.trazatex.apigateway.infrastructure.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.nodotextil.trazatex.apigateway.GatewayTestSupport;
import com.nodotextil.trazatex.apigateway.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {"trazatex.jwt.min-reload-interval=PT2S", "test.case=jwks-reload"})
class JwksReloadTest extends GatewayTestSupport {

    private static final long INTERVAL_MS = 2_000;

    private int jwksRequests() {
        return JWKS.countRequestsMatching(getRequestedFor(urlEqualTo(JWKS_PATH)).build()).getCount();
    }

    private int status(String token) {
        return anonymous.get().uri("/api/license").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                .returnResult(String.class).getStatus().value();
    }

    private void publish(RSAKey... keys) {
        JWKS.stubFor(get(urlEqualTo(JWKS_PATH)).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(TestTokens.jwks(keys))));
    }

    @Test
    void elJwksSeCacheaYUnKidDesconocidoSoloLoRecargaUnaVezPorIntervalo() throws InterruptedException {
        ORG_ACCESS.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        RSAKey key2 = TestTokens.generate("kid-2");
        RSAKey key3 = TestTokens.generate("kid-3");
        String token1 = TestTokens.operator();
        String token2 = TestTokens.sign(key2, TestTokens.operatorClaims().build());
        String token3 = TestTokens.sign(key3, TestTokens.operatorClaims().build());

        for (int i = 0; i < 20; i++) {
            assertThat(status(token1)).isEqualTo(200);
        }
        assertThat(jwksRequests()).as("una descarga, el resto sale de la caché").isEqualTo(1);

        for (int i = 0; i < 5; i++) {
            assertThat(status(token2)).isEqualTo(401);
        }
        assertThat(jwksRequests()).as("dentro del intervalo no se recarga").isEqualTo(1);

        publish(TestTokens.KEY, key2);
        assertThat(status(token2)).isEqualTo(401);
        assertThat(jwksRequests()).isEqualTo(1);

        Thread.sleep(INTERVAL_MS + 200);
        assertThat(status(token2)).isEqualTo(200);
        assertThat(jwksRequests()).as("una recarga").isEqualTo(2);
        for (int i = 0; i < 5; i++) {
            assertThat(status(token2)).isEqualTo(200);
        }
        assertThat(jwksRequests()).isEqualTo(2);

        for (int i = 0; i < 10; i++) {
            assertThat(status(token3)).isEqualTo(401);
        }
        assertThat(jwksRequests()).isEqualTo(2);

        Thread.sleep(INTERVAL_MS + 200);
        for (int i = 0; i < 10; i++) {
            assertThat(status(token3)).isEqualTo(401);
        }
        assertThat(jwksRequests()).as("una sola recarga para toda la ráfaga").isEqualTo(3);

        assertThat(status(token1)).isEqualTo(200);
    }
}
