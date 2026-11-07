package com.nodotextil.trazatex.quality.integration;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nodotextil.trazatex.quality.application.port.in.RecoverPendingCommandsUseCase;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US23 / US34 de punta a punta contra PostgreSQL real y un Production simulado (el comando interno): lo que hace el control con cada
 * respuesta de la tabla de communication.md, qué recibe el usuario, que con Production caído el control nunca queda {@code IN_PROGRESS}, que
 * repetir el comando con el mismo {@code requestId} no duplica nada y que el job cubre la caída entre el comando y el guardado.
 */
@SpringBootTest(properties = {
        "trazatex.messaging.enabled=false", "trazatex.quality.start.job-enabled=false",
        "trazatex.production.read-timeout=PT0.5S", "trazatex.production.connect-timeout=PT0.5S",
        "trazatex.production.retry.wait-duration=PT0.01S",
        "trazatex.quality.start.claims-per-run=3", "trazatex.quality.start.error-after-attempts=3"})
@ExtendWith(OutputCaptureExtension.class)
class ControlStartApiIT extends IntegrationTestBase {

    static final UUID COMPANY_A = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID COMPANY_B = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    static final UUID ADMIN_A = UUID.fromString("00000000-0000-0000-0000-00000000a0ad");
    static final UUID OPERATOR_A = UUID.fromString("00000000-0000-0000-0000-00000000a0a1");
    static final UUID ADMIN_B = UUID.fromString("00000000-0000-0000-0000-00000000b0ad");
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f0");

    record Caller(UUID userId, UUID companyId, String role) {
    }

    static final Caller ADMIN_OF_A = new Caller(ADMIN_A, COMPANY_A, "COMPANY_ADMIN");
    static final Caller OPERATOR_OF_A = new Caller(OPERATOR_A, COMPANY_A, "OPERATOR");
    static final Caller ADMIN_OF_B = new Caller(ADMIN_B, COMPANY_B, "COMPANY_ADMIN");
    static final Caller LICENSE_OWNER = new Caller(OWNER, null, "LICENSE_OWNER");

    @Autowired
    org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    BatchProjectionRepositoryPort projections;
    @Autowired
    RecoverPendingCommandsUseCase recovery;
    @Autowired
    CircuitBreakerRegistry breakers;
    @MockitoSpyBean
    QualityControlRepositoryPort controlsSpy;

    CircuitBreaker productionBreaker;

    @BeforeEach
    void resetBreaker() {
        productionBreaker = breakers.circuitBreaker("production");
        productionBreaker.reset();
    }

    // ---- ayudas ----

    UUID seed(BatchPhase phase, QualityStatus status, boolean hold, boolean everControlled) {
        BatchProjection batch = BatchProjection.restore(UUID.randomUUID(), COMPANY_A, phase, hold, status, everControlled, 1, START);
        projections.insert(batch);
        return batch.batchId();
    }

    UUID seedAvailable() {
        return seed(BatchPhase.AVAILABLE, QualityStatus.NOT_REVIEWED, false, false);
    }

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Caller caller) {
        MockHttpServletRequestBuilder builder = request.header("X-User-Id", caller.userId().toString()).header("X-User-Role", caller.role());
        return caller.companyId() == null ? builder : builder.header("X-Company-Id", caller.companyId().toString());
    }

    ResultActions startAs(Caller caller, UUID batch, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/quality/batches/" + batch + "/controls");
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(as(request, caller));
    }

    ResultActions start(UUID batch) throws Exception {
        return startAs(ADMIN_OF_A, batch, null);
    }

    UUID controlIdOf(ResultActions result) throws Exception {
        return UUID.fromString(json.readTree(result.andReturn().getResponse().getContentAsString()).path("id").asString());
    }

    <T> T column(UUID controlId, String column, Class<T> type) {
        return jdbc.sql("SELECT " + column + " FROM quality_control WHERE id = :id").param("id", controlId)
                .query((rs, n) -> rs.getObject(1, type)).list().get(0);
    }

    long controlsOf(UUID batch) {
        return jdbc.sql("SELECT count(*) FROM quality_control WHERE batch_id = :b").param("b", batch).query(Long.class).single();
    }

    List<Map<String, Object>> audit() {
        return jdbc.sql("SELECT action, user_id, company_id, entity_id, changes ->> 'reason' AS reason FROM audit_log ORDER BY occurred_at, action")
                .query().listOfRows();
    }

    List<String> requestIdsFor(UUID batch) {
        return ProductionStub.requestsFor(batch).stream().map(r -> json.readTree(r.getBodyAsString()).path("requestId").asString()).toList();
    }

    // ---- 200: Production confirma ----

    @Test
    void whenProductionConfirmsTheControlIsInProgressAndTheUserGets201_US23Scenario1() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answersOk();

        ResultActions result = start(batch);

        result.andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/quality/controls/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.controlType").value("EVALUATION"))
                .andExpect(jsonPath("$.batchId").value(batch.toString()))
                .andExpect(jsonPath("$.companyId").value(COMPANY_A.toString()))
                .andExpect(jsonPath("$.inspectorId").value(ADMIN_A.toString()))
                .andExpect(jsonPath("$.cancelReason").doesNotExist());
        UUID control = controlIdOf(result);
        assertThat(column(control, "status", String.class)).isEqualTo("IN_PROGRESS");
        assertThat(column(control, "next_attempt_at", OffsetDateTime.class)).as("ya no se reintenta").isNull();
        assertThat(column(control, "start_date", OffsetDateTime.class).toInstant()).isEqualTo(START);
        List<LoggedRequest> sent = ProductionStub.requestsFor(batch);
        assertThat(sent).hasSize(1);
        JsonNode body = json.readTree(sent.get(0).getBodyAsString());
        assertThat(body.path("operation").asString()).isEqualTo("START_EVALUATION");
        assertThat(body.path("requestId").asString()).isEqualTo(control.toString());
        assertThat(body.path("requestedBy").asString()).isEqualTo(ADMIN_A.toString());
        assertThat(audit()).hasSize(1);
        assertThat(audit().get(0)).containsEntry("action", "CONTROL_STARTED").containsEntry("user_id", ADMIN_A).containsEntry("company_id", COMPANY_A)
                .containsEntry("entity_id", control.toString());
    }

    static Stream<Arguments> controlTypes() {
        return Stream.of(
                Arguments.of(QualityStatus.NOT_REVIEWED, false, "EVALUATION"),
                Arguments.of(QualityStatus.PENDING_REEVALUATION, true, "REEVALUATION"),
                Arguments.of(QualityStatus.POTENTIAL_DERIVED_FAILURE, false, "EVALUATION"),
                Arguments.of(QualityStatus.POTENTIAL_DERIVED_FAILURE, true, "REEVALUATION"));
    }

    @ParameterizedTest(name = "{0}, tuvo control: {1} → {2}")
    @MethodSource("controlTypes")
    void theQualityStatusDecidesWhetherItIsAnEvaluationOrAReevaluation_US23Scenario2_US34(QualityStatus status, boolean ever, String type) throws Exception {
        UUID batch = seed(BatchPhase.AVAILABLE, status, status.retainsBatch(), ever);
        ProductionStub.answersOk();

        start(batch).andExpect(status().isCreated()).andExpect(jsonPath("$.controlType").value(type));
    }

    @Test
    void theOptionalInternalObservationsAreKept() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answersOk();

        ResultActions result = mockMvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/quality/batches/" + batch + "/controls").contentType(MediaType.APPLICATION_JSON)
                .content("{\"internalObservations\":\" revisar humedad \"}"), ADMIN_OF_A));

        result.andExpect(status().isCreated()).andExpect(jsonPath("$.internalObservations").value("revisar humedad"));
    }

    // ---- 409 / 404 / 400: Production no lo aplicó ----

    @ParameterizedTest
    @ValueSource(strings = {"INVALID_PHASE_TRANSITION", "BATCH_IS_FINAL_PRODUCT", "COMPANY_INACTIVE", "COMPANY_UNKNOWN"})
    void aConflictCancelsTheControlKeepingProductionsCodeAsTheReasonEvenThoughTheRequestRollsBack(String code) throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(409, ProductionStub.apiError(code));
        String key = UUID.randomUUID().toString();

        ResultActions result = startAs(ADMIN_OF_A, batch, key);

        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("mensaje de " + code)));
        UUID control = jdbc.sql("SELECT id FROM quality_control WHERE batch_id = :b").param("b", batch).query(UUID.class).single();
        assertThat(column(control, "status", String.class)).isEqualTo("CANCELLED");
        assertThat(column(control, "cancel_reason", String.class)).isEqualTo(code);
        assertThat(column(control, "next_attempt_at", OffsetDateTime.class)).isNull();
        assertThat(ProductionStub.requestsFor(batch)).as("un 409 no se reintenta").hasSize(1);
        assertThat(audit()).extracting(row -> row.get("action")).containsExactly("CONTROL_CANCELLED", "CONTROL_STARTED");
        assertThat(audit()).filteredOn(row -> "CONTROL_CANCELLED".equals(row.get("action"))).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("user_id", ADMIN_A).containsEntry("reason", code));
        // El filtro de idempotencia revirtió la clave (respuesta no 2xx), pero el control cancelado y su motivo se conservaron
        assertThat(jdbc.sql("SELECT count(*) FROM idempotency_keys").query(Long.class).single()).isZero();
    }

    @Test
    void afterACancellationTheBatchCanStartAnotherControl() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(409, ProductionStub.apiError("COMPANY_INACTIVE"));
        startAs(ADMIN_OF_A, batch, UUID.randomUUID().toString()).andExpect(status().isConflict());

        ProductionStub.reset();
        ProductionStub.answersOk();
        start(batch).andExpect(status().isCreated());

        assertThat(controlsOf(batch)).isEqualTo(2);
        assertThat(jdbc.sql("SELECT status FROM quality_control WHERE batch_id = :b ORDER BY status").param("b", batch).query(String.class).list())
                .containsExactly("CANCELLED", "IN_PROGRESS");
    }

    @Test
    void aProductionNotFoundCancelsTheControlAndTheUserSeesTheBatchDoesNotExist() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(404, ProductionStub.apiError("BATCH_NOT_FOUND"));

        start(batch).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BATCH_NOT_FOUND"));

        UUID control = jdbc.sql("SELECT id FROM quality_control WHERE batch_id = :b").param("b", batch).query(UUID.class).single();
        assertThat(column(control, "status", String.class)).isEqualTo("CANCELLED");
        assertThat(column(control, "cancel_reason", String.class)).isEqualTo("BATCH_NOT_FOUND");
    }

    @Test
    void aProductionBadRequestIsADefectOfQualityCancelsTheControlWithItsCodeAndLogsAnError(CapturedOutput output) throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(400, ProductionStub.apiError("MISSING_FIELDS"));

        start(batch).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        UUID control = jdbc.sql("SELECT id FROM quality_control WHERE batch_id = :b").param("b", batch).query(UUID.class).single();
        assertThat(column(control, "status", String.class)).as("no queda STARTING bloqueando el lote").isEqualTo("CANCELLED");
        assertThat(column(control, "cancel_reason", String.class)).isEqualTo("MISSING_FIELDS");
        assertThat(ProductionStub.requestsFor(batch)).hasSize(1);
        assertThat(output.getAll()).contains("ERROR").contains("la petición de Quality está mal");
    }

    // ---- Production caído: 202 y el control no queda IN_PROGRESS ----

    static Stream<Arguments> productionDown() {
        return Stream.of(
                Arguments.of("503", (Runnable) () -> ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"))),
                Arguments.of("500", (Runnable) () -> ProductionStub.answers(500, "")),
                Arguments.of("502", (Runnable) () -> ProductionStub.answers(502, "")),
                Arguments.of("timeout", (Runnable) () -> ProductionStub.SERVER.stubFor(post(urlPathMatching("/internal/batches/.*"))
                        .willReturn(aResponse().withStatus(200).withFixedDelay(3000)))),
                Arguments.of("conexión cortada", (Runnable) () -> ProductionStub.SERVER.stubFor(post(urlPathMatching("/internal/batches/.*"))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))),
                Arguments.of("200 ilegible", (Runnable) () -> ProductionStub.answers(200, "<html>proxy</html>")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productionDown")
    void withoutAnAnswerFromProductionTheUserGets202AndTheControlStaysStartingNeverInProgress(String failure, Runnable stub) throws Exception {
        UUID batch = seedAvailable();
        stub.run();

        ResultActions result = start(batch);

        result.andExpect(status().isAccepted())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/quality/controls/")))
                .andExpect(jsonPath("$.status").value("STARTING"));
        UUID control = controlIdOf(result);
        assertThat(column(control, "status", String.class)).isEqualTo("STARTING");
        assertThat(column(control, "attempts", Integer.class)).isEqualTo(1);
        assertThat(column(control, "last_error", String.class)).isNotBlank();
        assertThat(column(control, "next_attempt_at", OffsetDateTime.class).toInstant()).as("primer reintento a los 15 s").isEqualTo(START.plusSeconds(15));
        assertThat(column(control, "cancel_reason", String.class)).as("no se cancela por duda").isNull();
        assertThat(requestIdsFor(batch)).as("dos intentos con el mismo requestId").containsExactly(control.toString(), control.toString());
        assertThat(audit()).extracting(row -> row.get("action")).containsExactly("CONTROL_STARTED");
    }

    @Test
    void theJobFinishesTheStartWithTheSameRequestIdWithoutDuplicatingAnything() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        UUID control = controlIdOf(start(batch).andExpect(status().isAccepted()));
        assertThat(recovery.recoverPending()).as("aún no vence").isZero();

        ProductionStub.reset();
        ProductionStub.answersOk();
        clock.advance(Duration.ofSeconds(15));
        assertThat(recovery.recoverPending()).isEqualTo(1);

        assertThat(column(control, "status", String.class)).isEqualTo("IN_PROGRESS");
        assertThat(column(control, "next_attempt_at", OffsetDateTime.class)).isNull();
        assertThat(controlsOf(batch)).isEqualTo(1);
        assertThat(requestIdsFor(batch)).as("el job repitió el comando con el mismo requestId").containsExactly(control.toString());
        clock.advance(Duration.ofHours(1));
        assertThat(recovery.recoverPending()).as("ya no hay nada que reintentar").isZero();
        assertThat(requestIdsFor(batch)).hasSize(1);
        assertThat(audit()).extracting(row -> row.get("action")).containsExactly("CONTROL_STARTED");
    }

    @Test
    void ifProductionRejectsTheRetryTheJobCancelsTheControlWithTheCodeAsReasonAndAuditsItWithTheControlsUser() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        UUID control = controlIdOf(start(batch).andExpect(status().isAccepted()));

        ProductionStub.reset();
        ProductionStub.answers(409, ProductionStub.apiError("COMPANY_INACTIVE"));
        clock.advance(Duration.ofSeconds(15));
        recovery.recoverPending();

        assertThat(column(control, "status", String.class)).isEqualTo("CANCELLED");
        assertThat(column(control, "cancel_reason", String.class)).isEqualTo("COMPANY_INACTIVE");
        assertThat(audit()).filteredOn(row -> "CONTROL_CANCELLED".equals(row.get("action"))).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("user_id", ADMIN_A).containsEntry("company_id", COMPANY_A));
    }

    @Test
    void repeatingTheRequestWithTheSameIdempotencyKeyReturnsTheSame202WithoutCallingProductionAgain() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        String key = UUID.randomUUID().toString();
        UUID control = controlIdOf(startAs(ADMIN_OF_A, batch, key).andExpect(status().isAccepted()));
        int callsBefore = ProductionStub.requestsFor(batch).size();

        ResultActions again = startAs(ADMIN_OF_A, batch, key);

        again.andExpect(status().isAccepted()).andExpect(jsonPath("$.id").value(control.toString())).andExpect(jsonPath("$.status").value("STARTING"));
        assertThat(controlsOf(batch)).isEqualTo(1);
        assertThat(ProductionStub.requestsFor(batch)).hasSize(callsBefore);
    }

    @Test
    void anotherKeyWhileTheControlIsStillStartingIsRefusedWithoutCallingProduction() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        start(batch).andExpect(status().isAccepted());
        int callsBefore = ProductionStub.requestsFor(batch).size();

        startAs(ADMIN_OF_A, batch, UUID.randomUUID().toString()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTROL_ALREADY_ACTIVE"));

        assertThat(controlsOf(batch)).isEqualTo(1);
        assertThat(ProductionStub.requestsFor(batch)).hasSize(callsBefore);
    }

    // ---- caída entre el comando y el guardado ----

    @Test
    void ifQualityDiesBetweenTheCommandAndSavingTheAnswerTheJobRepeatsTheSameCommandAndTheControlEndsInProgress() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answersOk();   // Production SÍ aplicó el comando...
        Mockito.doThrow(new IllegalStateException("el proceso cayó antes de guardar la respuesta")).doCallRealMethod()
                .when(controlsSpy).findByIdForUpdate(any());   // ...pero Quality no llegó a guardarlo

        start(batch).andExpect(status().isInternalServerError());

        UUID control = jdbc.sql("SELECT id FROM quality_control WHERE batch_id = :b").param("b", batch).query(UUID.class).single();
        assertThat(column(control, "status", String.class)).as("el control ya estaba guardado STARTING antes de llamar").isEqualTo("STARTING");
        var initialRequests = requestIdsFor(batch);
        assertThat(initialRequests).containsOnly(control.toString());
        assertThat(initialRequests.size()).isBetween(1, 2);
        assertThat(recovery.recoverPending()).as("dentro de la gracia de 30 s la petición sigue siendo la dueña").isZero();

        clock.advance(Duration.ofSeconds(31));
        assertThat(recovery.recoverPending()).isEqualTo(1);

        assertThat(column(control, "status", String.class)).isEqualTo("IN_PROGRESS");
        assertThat(controlsOf(batch)).isEqualTo(1);
        assertThat(requestIdsFor(batch)).as("cada intento conserva el requestId, incluso si el cliente repite tras un timeout")
                .containsOnly(control.toString());
        assertThat(requestIdsFor(batch).size()).isBetween(initialRequests.size() + 1, initialRequests.size() + 2);
    }

    // ---- circuit breaker ----

    @Test
    void withTheCircuitOpenNothingIsSentTheUserGets202AndNoAttemptIsCounted() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answersOk();
        productionBreaker.transitionToOpenState();

        ResultActions result = start(batch);
        result.andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("STARTING"));
        UUID control = controlIdOf(result);
        clock.advance(Duration.ofSeconds(15));
        assertThat(recovery.recoverPending()).isEqualTo(1);

        assertThat(ProductionStub.requestsFor(batch)).as("el circuito abierto no deja pasar nada").isEmpty();
        assertThat(column(control, "attempts", Integer.class)).as("no se llamó: no cuenta como intento").isZero();
        assertThat(column(control, "status", String.class)).isEqualTo("STARTING");

        productionBreaker.transitionToHalfOpenState();
        productionBreaker.transitionToClosedState();
        clock.advance(Duration.ofSeconds(15));
        assertThat(recovery.recoverPending()).isEqualTo(1);
        assertThat(column(control, "status", String.class)).isEqualTo("IN_PROGRESS");
    }

    // ---- espera creciente, sin tope de intentos ----

    @Test
    void theWaitGrowsFrom15SecondsToFiveMinutesTheControlIsNeverCancelledAndFromTheThirdAttemptEachFailureIsAnError(CapturedOutput output) throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        UUID control = controlIdOf(start(batch).andExpect(status().isAccepted()));
        List<Long> waits = new ArrayList<>();
        waits.add(Duration.between(clock.instant(), column(control, "next_attempt_at", OffsetDateTime.class).toInstant()).toSeconds());

        for (int attempt = 2; attempt <= 8; attempt++) {
            productionBreaker.reset();
            clock.advance(Duration.between(clock.instant(), column(control, "next_attempt_at", OffsetDateTime.class).toInstant()));
            assertThat(recovery.recoverPending()).isEqualTo(1);
            waits.add(Duration.between(clock.instant(), column(control, "next_attempt_at", OffsetDateTime.class).toInstant()).toSeconds());
            assertThat(column(control, "attempts", Integer.class)).isEqualTo(attempt);
        }

        assertThat(waits).containsExactly(15L, 30L, 60L, 120L, 240L, 300L, 300L, 300L);
        assertThat(column(control, "status", String.class)).as("sin tope de intentos: nunca se cancela por cansancio").isEqualTo("STARTING");
        assertThat(output.getAll()).contains("sigue en STARTING tras 3 intentos").contains("Control atascado en STARTING");
    }

    // ---- varias instancias del job ----

    @Test
    void severalJobInstancesNeverTakeTheSameControlTwice() throws Exception {
        List<UUID> batches = new ArrayList<>();
        List<UUID> controls = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            BatchProjection batch = BatchProjection.restore(UUID.randomUUID(), COMPANY_A, BatchPhase.AVAILABLE, false, QualityStatus.NOT_REVIEWED, false, 1, START);
            QualityControl control = QualityControl.start(UUID.randomUUID(), batch, ADMIN_A, null, START);
            controlsSpy.insertStarting(control, START);
            batches.add(batch.batchId());
            controls.add(control.id());
        }
        ProductionStub.SERVER.stubFor(post(urlPathMatching("/internal/batches/.*/phase-transitions")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withFixedDelay(60)
                .withBody("{\"batchId\":\"00000000-0000-0000-0000-000000000000\",\"phase\":\"IN_QUALITY_CONTROL\",\"qualityHold\":false}")));
        clock.advance(Duration.ofSeconds(1));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> runs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                runs.add(pool.submit(() -> {
                    int total = 0;
                    int claimed;
                    while ((claimed = recovery.recoverPending()) > 0) {
                        total += claimed;
                    }
                    return total;
                }));
            }
            int claimedInTotal = 0;
            for (Future<Integer> run : runs) {
                claimedInTotal += run.get(60, TimeUnit.SECONDS);
            }
            assertThat(claimedInTotal).isEqualTo(12);
        } finally {
            pool.shutdownNow();
        }

        for (int i = 0; i < 12; i++) {
            assertThat(column(controls.get(i), "status", String.class)).isEqualTo("IN_PROGRESS");
            assertThat(requestIdsFor(batches.get(i))).as("el comando de cada control salió una sola vez").containsExactly(controls.get(i).toString());
        }
    }

    // ---- un solo control activo por lote ----

    @Test
    void twoSimultaneousStartsOnTheSameBatchLetExactlyOneThrough() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.SERVER.stubFor(post(urlPathMatching("/internal/batches/.*/phase-transitions")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withFixedDelay(300)
                .withBody("{\"batchId\":\"00000000-0000-0000-0000-000000000000\",\"phase\":\"IN_QUALITY_CONTROL\",\"qualityHold\":false}")));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                calls.add(pool.submit(() -> startAs(ADMIN_OF_A, batch, UUID.randomUUID().toString()).andReturn().getResponse().getStatus()));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> call : calls) {
                statuses.add(call.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }

        assertThat(controlsOf(batch)).isEqualTo(1);
        assertThat(ProductionStub.requestsFor(batch)).hasSize(1);
    }

    // ---- reglas de dominio: ni siquiera se llama a Production ----

    static Stream<Arguments> unstartableBatches() {
        return Stream.of(
                Arguments.of(BatchPhase.AVAILABLE, QualityStatus.CONFORMING, false, true, "BATCH_ALREADY_CONFORMING"),
                Arguments.of(BatchPhase.AVAILABLE, QualityStatus.FAILED, true, true, "BATCH_FAILED_REQUIRES_REEVALUATION"),
                Arguments.of(BatchPhase.IN_QUALITY_CONTROL, QualityStatus.NOT_REVIEWED, false, false, "CONTROL_ALREADY_ACTIVE"),
                Arguments.of(BatchPhase.IN_TRANSFER, QualityStatus.NOT_REVIEWED, false, false, "BATCH_NOT_AVAILABLE_FOR_CONTROL"),
                Arguments.of(BatchPhase.IN_TRANSFORMATION, QualityStatus.NOT_REVIEWED, false, false, "BATCH_NOT_AVAILABLE_FOR_CONTROL"),
                Arguments.of(BatchPhase.DISCARDED, QualityStatus.FAILED, true, true, "BATCH_NOT_AVAILABLE_FOR_CONTROL"),
                Arguments.of(BatchPhase.PROCESSED, QualityStatus.CONFORMING, false, true, "BATCH_NOT_AVAILABLE_FOR_CONTROL"));
    }

    @ParameterizedTest(name = "{0} / {1} → {4}")
    @MethodSource("unstartableBatches")
    void aBatchThatCannotBeControlledIsRefusedWithoutCreatingAControlOrCallingProduction(BatchPhase phase, QualityStatus quality, boolean hold,
                                                                                      boolean ever, String code) throws Exception {
        UUID batch = seed(phase, quality, hold, ever);
        ProductionStub.answersOk();

        start(batch).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code));

        assertThat(controlsOf(batch)).isZero();
        assertThat(ProductionStub.allRequests()).isEmpty();
        assertThat(audit()).isEmpty();
    }

    @Test
    void aBatchWhoseQualityStatusIsNotKnownYetIsRefused() throws Exception {
        UUID batch = UUID.randomUUID();
        projections.insert(BatchProjection.partial(batch, COMPANY_A, BatchPhase.AVAILABLE, false, 3, START));

        start(batch).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BATCH_QUALITY_UNKNOWN"));
        assertThat(ProductionStub.allRequests()).isEmpty();
    }

    // ---- otra empresa y permisos (convención de Production: lo ajeno «no existe») ----

    @Test
    void aBatchOfAnotherCompanyIsExactlyLikeOneThatDoesNotExist() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answersOk();

        String foreign = startAs(ADMIN_OF_B, batch, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BATCH_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String missing = startAs(ADMIN_OF_B, UUID.randomUUID(), null).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(withoutTimestamp(foreign)).isEqualTo(withoutTimestamp(missing));
        assertThat(controlsOf(batch)).isZero();
        assertThat(ProductionStub.allRequests()).isEmpty();
    }

    @Test
    void aControlOfAnotherCompanyIsExactlyLikeOneThatDoesNotExist() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answersOk();
        UUID control = controlIdOf(start(batch));

        String foreign = mockMvc.perform(as(get("/api/quality/controls/" + control), ADMIN_OF_B)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTROL_NOT_FOUND")).andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(as(get("/api/quality/controls/" + UUID.randomUUID()), ADMIN_OF_B)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(withoutTimestamp(foreign)).isEqualTo(withoutTimestamp(missing));
    }

    @Test
    void theOwnCompanyReadsItsControlIncludingTheCancellationReason() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(409, ProductionStub.apiError("BATCH_IS_FINAL_PRODUCT"));
        start(batch).andExpect(status().isConflict());
        UUID control = jdbc.sql("SELECT id FROM quality_control WHERE batch_id = :b").param("b", batch).query(UUID.class).single();

        mockMvc.perform(as(get("/api/quality/controls/" + control), ADMIN_OF_A)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(control.toString())).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("BATCH_IS_FINAL_PRODUCT")).andExpect(jsonPath("$.tests").isEmpty());
    }

    @Test
    void pollingAControlThatIsStillStartingShowsItAndThenInProgress() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        UUID control = controlIdOf(start(batch).andExpect(status().isAccepted()));
        mockMvc.perform(as(get("/api/quality/controls/" + control), ADMIN_OF_A)).andExpect(jsonPath("$.status").value("STARTING"));

        ProductionStub.reset();
        ProductionStub.answersOk();
        clock.advance(Duration.ofSeconds(15));
        recovery.recoverPending();

        mockMvc.perform(as(get("/api/quality/controls/" + control), ADMIN_OF_A)).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void onlyACompanyAdminStartsOrReadsControls() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answersOk();

        startAs(OPERATOR_OF_A, batch, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        startAs(LICENSE_OWNER, batch, null).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/quality/controls/" + UUID.randomUUID()), OPERATOR_OF_A)).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/quality/batches/" + batch + "/controls"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/quality/controls/" + UUID.randomUUID())).andExpect(status().isUnauthorized());

        assertThat(controlsOf(batch)).isZero();
        assertThat(ProductionStub.allRequests()).isEmpty();
    }

    @Test
    void aBatchIdThatIsNotAUuidIsABadRequest() throws Exception {
        mockMvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/quality/batches/no-es-uuid/controls"), ADMIN_OF_A))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    private String withoutTimestamp(String apiError) {
        return json.readTree(apiError).propertyNames().stream().filter(name -> !name.equals("timestamp"))
                .map(name -> name + "=" + json.readTree(apiError).path(name)).sorted().toList().toString();
    }
}
