package com.nodotextil.trazatex.apigateway.infrastructure.web;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nodotextil.trazatex.apigateway.GatewayTestSupport;
import com.nodotextil.trazatex.apigateway.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 06b: las rutas de {@code /api/quality/**} llegan a quality con la identidad que inyecta el Gateway y con la {@code Idempotency-Key}; la
 * respuesta 202 (Production caído, el control sigue {@code STARTING}) pasa tal cual, con su {@code Location} y {@code Retry-After}. El comando
 * interno Quality → Production ({@code /internal/**}) sigue sin salir por el Gateway.
 */
class QualityRoutingTest extends GatewayTestSupport {

    @BeforeEach
    void stubQuality() {
        QUALITY.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"ok\":true}")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/quality/batches/1/controls", "/api/quality/controls/2/tests", "/api/quality/controls/2/finish",
            "/api/quality/failures/3", "/api/quality/controls/2/report"})
    void qualityRoutesReachQualityAsTheyAreWithTheIdentityTheGatewayInjects(String path) {
        client.post().uri(path + "?x=1").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"a\":1}").exchange().expectStatus().isOk();

        LoggedRequest received = QUALITY.findAll(anyRequestedFor(anyUrl())).getFirst();
        assertThat(received.getUrl()).isEqualTo(path + "?x=1");
        assertThat(received.header("X-User-Id").firstValue()).isEqualTo(TestTokens.USER_ID.toString());
        assertThat(received.header("X-Company-Id").firstValue()).isEqualTo(TestTokens.COMPANY_ID.toString());
        assertThat(received.header("X-User-Role").firstValue()).isEqualTo("OPERATOR");
        assertThat(PRODUCTION.findAll(anyRequestedFor(anyUrl()))).isEmpty();
        assertThat(TRACEABILITY.findAll(anyRequestedFor(anyUrl()))).isEmpty();
        assertThat(ORG_ACCESS.findAll(anyRequestedFor(anyUrl()))).isEmpty();
    }

    @Test
    void theIdempotencyKeyReachesQualityAndTheAcceptedResponseIsPassedThroughUntouched() {
        UUID key = UUID.randomUUID();
        QUALITY.resetAll();
        QUALITY.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(202).withHeader("Content-Type", "application/json")
                .withHeader("Location", "/api/quality/controls/9").withHeader("Retry-After", "5").withBody("{\"status\":\"STARTING\"}")));

        client.post().uri("/api/quality/batches/1/controls").header("Idempotency-Key", key.toString()).exchange()
                .expectStatus().isAccepted()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/api/quality/controls/9")
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "5")
                .expectBody().jsonPath("$.status").isEqualTo("STARTING");

        assertThat(QUALITY.findAll(anyRequestedFor(anyUrl())).getFirst().header("Idempotency-Key").firstValue()).isEqualTo(key.toString());
    }

    @Test
    void aQualityErrorIsPassedThroughWithItsCode() {
        QUALITY.resetAll();
        QUALITY.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(409).withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"INVALID_PHASE_TRANSITION\",\"message\":\"x\",\"details\":[]}")));

        client.post().uri("/api/quality/batches/1/controls").exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_PHASE_TRANSITION");
    }

    @Test
    void withoutATokenTheQualityRoutesAskForOne() {
        anonymous.post().uri("/api/quality/batches/1/controls").exchange().expectStatus().isUnauthorized();

        assertThat(QUALITY.findAll(anyRequestedFor(anyUrl()))).isEmpty();
    }

    @Test
    void theInternalCommandStillDoesNotLeaveThroughTheGateway() {
        client.post().uri("/internal/batches/1/phase-transitions").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isNotFound();

        assertThat(QUALITY.findAll(anyRequestedFor(anyUrl()))).isEmpty();
        assertThat(PRODUCTION.findAll(anyRequestedFor(anyUrl()))).isEmpty();
    }
}
