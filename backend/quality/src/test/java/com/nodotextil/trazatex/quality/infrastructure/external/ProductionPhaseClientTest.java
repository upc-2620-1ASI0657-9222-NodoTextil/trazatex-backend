package com.nodotextil.trazatex.quality.infrastructure.external;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nodotextil.trazatex.quality.application.port.out.PhaseCommand;
import com.nodotextil.trazatex.quality.application.port.out.PhaseCommandOutcome;
import com.nodotextil.trazatex.quality.application.port.out.PhaseOperation;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * El cliente del comando interno contra un Production simulado: cada fila de la tabla «Respuestas y cómo debe reaccionar Quality»,
 * el retry acotado, el circuit breaker y que <b>jamás</b> se fabrica un éxito.
 */
class ProductionPhaseClientTest {

    static final ObjectMapper MAPPER = JsonMapper.builder().build();
    static WireMockServer production;

    final UUID batch = UUID.randomUUID();
    final UUID control = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final String path = "/internal/batches/" + batch + "/phase-transitions";
    CircuitBreaker breaker;
    ProductionPhaseClient client;

    @BeforeAll
    static void startProduction() {
        production = new WireMockServer(wireMockConfig().dynamicPort());
        production.start();
    }

    @AfterAll
    static void stopProduction() {
        production.stop();
    }

    @BeforeEach
    void newClient() {
        production.resetAll();
        client = clientFor("http://localhost:" + production.port(), new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 2));
    }

    /** Timeouts de la prueba más cortos que los de producción (500 ms de lectura, 10 ms entre intentos); la forma es la misma. */
    ProductionPhaseClient clientFor(String baseUrl, CircuitBreakerSettings settings) {
        breaker = CircuitBreakerRegistry.ofDefaults().circuitBreaker("production-" + UUID.randomUUID(), settings.toConfig());
        return new ProductionPhaseClient(ExternalHttpClients.restClient(baseUrl, Duration.ofMillis(500), Duration.ofMillis(500)), breaker,
                new ProductionClientProperties.Retry(2, Duration.ofMillis(10)), MAPPER);
    }

    PhaseCommandOutcome start() {
        return client.send(PhaseCommand.startEvaluation(batch, control, user));
    }

    void stubResponse(int status, String body) {
        production.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    static String apiError(String code) {
        return "{\"code\":\"" + code + "\",\"message\":\"mensaje de " + code + "\",\"details\":[],\"timestamp\":\"2026-10-23T12:00:00Z\"}";
    }

    List<LoggedRequest> requests() {
        return production.findAll(postRequestedFor(urlPathEqualTo(path)));
    }

    // ---- 200 ----

    @Test
    void a200ConfirmsAndTheCommandCarriesTheControlIdAsRequestId() {
        stubResponse(200, "{\"batchId\":\"" + batch + "\",\"phase\":\"IN_QUALITY_CONTROL\",\"qualityHold\":false}");

        PhaseCommandOutcome outcome = start();

        assertThat(outcome).isEqualTo(new PhaseCommandOutcome.Confirmed(batch, BatchPhase.IN_QUALITY_CONTROL, false));
        assertThat(requests()).hasSize(1);
        JsonNode sent = MAPPER.readTree(requests().get(0).getBodyAsString());
        assertThat(sent.path("operation").asString()).isEqualTo("START_EVALUATION");
        assertThat(sent.path("requestId").asString()).isEqualTo(control.toString());
        assertThat(sent.path("requestedBy").asString()).isEqualTo(user.toString());
        assertThat(sent.has("qualityHold")).as("qualityHold solo viaja en RELEASE").isFalse();
        assertThat(requests().get(0).getHeader("Content-Type")).startsWith("application/json");
    }

    @Test
    void releaseCarriesTheQualityHoldAndHoldNeedsNoRequestedBy() {
        stubResponse(200, "{\"batchId\":\"" + batch + "\",\"phase\":\"AVAILABLE\",\"qualityHold\":true}");

        client.send(new PhaseCommand(batch, PhaseOperation.RELEASE, control.toString(), true, "CONTROL_FAILED", user));
        client.send(new PhaseCommand(batch, PhaseOperation.HOLD, UUID.randomUUID().toString(), null, null, null));

        JsonNode release = MAPPER.readTree(requests().get(0).getBodyAsString());
        assertThat(release.path("operation").asString()).isEqualTo("RELEASE");
        assertThat(release.path("qualityHold").asBoolean()).isTrue();
        assertThat(release.path("reason").asString()).isEqualTo("CONTROL_FAILED");
        JsonNode hold = MAPPER.readTree(requests().get(1).getBodyAsString());
        assertThat(hold.has("requestedBy")).isFalse();
        assertThat(hold.has("qualityHold")).isFalse();
    }

    @Test
    void a200WithAnUnreadableBodyIsNotAConfirmationBecauseItIsNotKnownWhatWasApplied() {
        stubResponse(200, "<html>proxy</html>");

        assertThat(start()).isInstanceOf(PhaseCommandOutcome.Unavailable.class);
        assertThat(requests()).as("se reintentó una vez").hasSize(2);
    }

    // ---- 409 y 404: rechazo de negocio, sin reintento ----

    @ParameterizedTest
    @ValueSource(strings = {"INVALID_PHASE_TRANSITION", "BATCH_IS_FINAL_PRODUCT", "COMPANY_INACTIVE", "COMPANY_UNKNOWN"})
    void a409IsABusinessRejectionWithProductionsCodeAndIsNeverRetried(String code) {
        stubResponse(409, apiError(code));

        PhaseCommandOutcome outcome = start();

        assertThat(outcome).isEqualTo(new PhaseCommandOutcome.Rejected(409, code, "mensaje de " + code));
        assertThat(requests()).hasSize(1);
    }

    @Test
    void a404BatchNotFoundIsARejectionToo() {
        stubResponse(404, apiError("BATCH_NOT_FOUND"));

        assertThat(start()).isEqualTo(new PhaseCommandOutcome.Rejected(404, "BATCH_NOT_FOUND", "mensaje de BATCH_NOT_FOUND"));
        assertThat(requests()).hasSize(1);
    }

    @Test
    void aRejectionWithoutAReadableCodeStillGetsAReasonThatFitsTheColumn() {
        stubResponse(409, "no es json");
        PhaseCommandOutcome withoutCode = start();
        production.resetAll();
        stubResponse(409, apiError("X".repeat(300)));
        PhaseCommandOutcome tooLong = start();

        assertThat(withoutCode).isEqualTo(new PhaseCommandOutcome.Rejected(409, "HTTP_409", "HTTP 409"));
        assertThat(((PhaseCommandOutcome.Rejected) tooLong).code()).hasSize(100);
    }

    // ---- 400: defecto de Quality ----

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 405, 415, 422})
    void aClientErrorThatIsNotAContractRejectionIsADefectAndIsNeverRetried(int status) {
        stubResponse(status, apiError("MISSING_FIELDS"));

        PhaseCommandOutcome outcome = start();

        assertThat(outcome).isEqualTo(new PhaseCommandOutcome.Defective(status, "MISSING_FIELDS", "mensaje de MISSING_FIELDS"));
        assertThat(requests()).hasSize(1);
    }

    // ---- sin decisión de Production: se reintenta, con el mismo requestId ----

    @Test
    void a503IsRetriedOnceWithTheSameBodyAndThenIsUnavailableNotAFailure() {
        production.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withStatus(503).withHeader("Retry-After", "1")
                .withHeader("Content-Type", "application/json").withBody(apiError("TEMPORARILY_UNAVAILABLE"))));

        PhaseCommandOutcome outcome = start();

        assertThat(outcome).isInstanceOfSatisfying(PhaseCommandOutcome.Unavailable.class, u -> {
            assertThat(u.circuitOpen()).isFalse();
            assertThat(u.cause()).contains("503");
        });
        assertThat(requests()).hasSize(2);
        assertThat(requests().get(0).getBodyAsString()).isEqualTo(requests().get(1).getBodyAsString());
        assertThat(requests().get(1).getBodyAsString()).contains(control.toString());
    }

    @Test
    void a503ThatRecoversOnTheRetryConfirms() {
        production.stubFor(post(urlPathEqualTo(path)).inScenario("race").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503).withBody(apiError("TEMPORARILY_UNAVAILABLE"))).willSetStateTo("second"));
        production.stubFor(post(urlPathEqualTo(path)).inScenario("race").whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"batchId\":\"" + batch + "\",\"phase\":\"IN_QUALITY_CONTROL\",\"qualityHold\":false}")));

        assertThat(start()).isInstanceOf(PhaseCommandOutcome.Confirmed.class);
        assertThat(requests()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 504})
    void anyOther5xxIsTreatedAsNoAnswerBecauseProductionMayHaveAppliedTheCommand(int status) {
        stubResponse(status, "");

        assertThat(start()).isInstanceOf(PhaseCommandOutcome.Unavailable.class);
        assertThat(requests()).hasSize(2);
    }

    @Test
    void aTimeoutIsNoAnswerAndIsRetriedOnce() {
        production.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withStatus(200).withFixedDelay(2000)));

        long started = System.nanoTime();
        PhaseCommandOutcome outcome = start();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(outcome).isInstanceOf(PhaseCommandOutcome.Unavailable.class);
        assertThat(requests()).hasSize(2);
        // dos lecturas de 500 ms cortadas por el timeout; la respuesta tardaba 2 s cada una
        assertThat(elapsed).isBetween(Duration.ofMillis(900), Duration.ofMillis(1900));
    }

    @Test
    void aDroppedConnectionIsNoAnswer() {
        production.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThat(start()).isInstanceOf(PhaseCommandOutcome.Unavailable.class);
        assertThat(requests()).hasSize(2);
    }

    @Test
    void anUnreachableProductionIsNoAnswer() {
        ProductionPhaseClient unreachable = clientFor("http://localhost:1", new CircuitBreakerSettings(10, 5, 50, Duration.ofSeconds(30), 2));

        PhaseCommandOutcome outcome = unreachable.send(PhaseCommand.startEvaluation(batch, control, user));

        assertThat(outcome).isInstanceOfSatisfying(PhaseCommandOutcome.Unavailable.class, u -> assertThat(u.circuitOpen()).isFalse());
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 204, 302})
    void aResponseOutsideTheContractIsNeverTakenAsSuccess(int status) {
        stubResponse(status, "");

        assertThat(start()).isInstanceOf(PhaseCommandOutcome.Unavailable.class);
    }

    @Test
    void thereIsNoWayToGetAConfirmationWithoutProductionAnswering200() {
        int[] statuses = {201, 202, 204, 301, 302, 400, 401, 403, 404, 405, 409, 415, 422, 429, 500, 502, 503, 504};
        for (int status : statuses) {
            production.resetAll();
            stubResponse(status, apiError("ANY"));
            assertThat(start()).as("HTTP %d", status).isNotInstanceOf(PhaseCommandOutcome.Confirmed.class);
        }
        production.resetAll();
        production.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));
        assertThat(start()).isNotInstanceOf(PhaseCommandOutcome.Confirmed.class);
    }

    // ---- circuit breaker ----

    @Test
    void afterEnoughFailuresTheCircuitOpensAndNothingReachesProductionAnymore() {
        client = clientFor("http://localhost:" + production.port(), new CircuitBreakerSettings(4, 4, 50, Duration.ofSeconds(30), 1));
        stubResponse(503, apiError("TEMPORARILY_UNAVAILABLE"));

        start();   // 2 intentos = 2 fallos
        start();   // 2 fallos más: 4 de 4, el circuito abre
        int before = requests().size();
        PhaseCommandOutcome whileOpen = start();

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(whileOpen).isEqualTo(new PhaseCommandOutcome.Unavailable("el circuito de Production está abierto", true));
        assertThat(requests()).as("con el circuito abierto no se llama ni se reintenta").hasSize(before);
    }

    @Test
    void businessRejectionsAreNormalAnswersAndNeverOpenTheCircuit() {
        client = clientFor("http://localhost:" + production.port(), new CircuitBreakerSettings(4, 4, 50, Duration.ofSeconds(30), 1));
        stubResponse(409, apiError("INVALID_PHASE_TRANSITION"));

        for (int i = 0; i < 12; i++) {
            assertThat(start()).isInstanceOf(PhaseCommandOutcome.Rejected.class);
        }
        production.resetAll();
        stubResponse(400, apiError("MISSING_FIELDS"));
        for (int i = 0; i < 12; i++) {
            assertThat(start()).isInstanceOf(PhaseCommandOutcome.Defective.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void theConfiguredFiveCallMinimumOpensOnlyAfterFiveFailures() {
        stubResponse(503, apiError("TEMPORARILY_UNAVAILABLE"));
        start();
        start();
        assertThat(requests()).hasSize(4);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        start();
        assertThat(requests()).hasSize(5);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(start()).isInstanceOf(PhaseCommandOutcome.Unavailable.class);
        assertThat(requests()).hasSize(5);
    }

    @Test
    void theCircuitClosesAgainOnceProductionRecoversAfterTheWait() throws Exception {
        client = clientFor("http://localhost:" + production.port(), new CircuitBreakerSettings(4, 4, 50, Duration.ofMillis(200), 1));
        stubResponse(503, apiError("TEMPORARILY_UNAVAILABLE"));
        start();
        start();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        production.resetAll();
        stubResponse(200, "{\"batchId\":\"" + batch + "\",\"phase\":\"IN_QUALITY_CONTROL\",\"qualityHold\":false}");
        Thread.sleep(300);

        assertThat(start()).isInstanceOf(PhaseCommandOutcome.Confirmed.class);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
