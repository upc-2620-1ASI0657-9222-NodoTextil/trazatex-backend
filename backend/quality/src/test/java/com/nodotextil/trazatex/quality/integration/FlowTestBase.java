package com.nodotextil.trazatex.quality.integration;

import com.nodotextil.trazatex.quality.application.port.in.RecoverPendingCommandsUseCase;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Lo común de las pruebas de flujo (control, falla, falla derivada) contra PostgreSQL real y un Production simulado. */
abstract class FlowTestBase extends IntegrationTestBase {

    static final UUID COMPANY_A = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID COMPANY_B = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    static final UUID ADMIN_A = UUID.fromString("00000000-0000-0000-0000-00000000a0ad");
    static final UUID OTHER_ADMIN_A = UUID.fromString("00000000-0000-0000-0000-00000000a0ae");
    static final UUID OPERATOR_A = UUID.fromString("00000000-0000-0000-0000-00000000a0a1");
    static final UUID ADMIN_B = UUID.fromString("00000000-0000-0000-0000-00000000b0ad");

    record Caller(UUID userId, UUID companyId, String role) {
    }

    static final Caller ADMIN_OF_A = new Caller(ADMIN_A, COMPANY_A, "COMPANY_ADMIN");
    static final Caller OTHER_ADMIN_OF_A = new Caller(OTHER_ADMIN_A, COMPANY_A, "COMPANY_ADMIN");
    static final Caller OPERATOR_OF_A = new Caller(OPERATOR_A, COMPANY_A, "OPERATOR");
    static final Caller ADMIN_OF_B = new Caller(ADMIN_B, COMPANY_B, "COMPANY_ADMIN");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    BatchProjectionRepositoryPort projections;
    @Autowired
    RecoverPendingCommandsUseCase recovery;
    @Autowired
    CircuitBreakerRegistry breakers;

    CircuitBreaker productionBreaker;

    @BeforeEach
    void resetBreaker() {
        productionBreaker = breakers.circuitBreaker("production");
        productionBreaker.reset();
    }

    // ---- datos ----

    UUID seed(BatchPhase phase, QualityStatus status, boolean hold, boolean everControlled) {
        BatchProjection batch = BatchProjection.restore(UUID.randomUUID(), COMPANY_A, phase, hold, status, everControlled, 1, START);
        projections.insert(batch);
        return batch.batchId();
    }

    UUID seedAvailable() {
        return seed(BatchPhase.AVAILABLE, QualityStatus.NOT_REVIEWED, false, false);
    }

    BatchProjection projection(UUID batch) {
        return projections.findById(batch).orElseThrow();
    }

    // ---- peticiones ----

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Caller caller) {
        MockHttpServletRequestBuilder builder = request.header("X-User-Id", caller.userId().toString()).header("X-User-Role", caller.role());
        return caller.companyId() == null ? builder : builder.header("X-Company-Id", caller.companyId().toString());
    }

    ResultActions postAs(Caller caller, String url) throws Exception {
        return mockMvc.perform(as(post(url), caller));
    }

    ResultActions postAs(Caller caller, String url, Object body) throws Exception {
        return mockMvc.perform(as(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), caller));
    }

    ResultActions putAs(Caller caller, String url, Object body) throws Exception {
        return mockMvc.perform(as(put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), caller));
    }

    ResultActions getAs(Caller caller, String url) throws Exception {
        return mockMvc.perform(as(get(url), caller));
    }

    JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Inicia un control sobre el lote con Production confirmando, y devuelve su id. */
    UUID startControl(UUID batch) throws Exception {
        productionBreaker.reset();
        ProductionStub.reset();
        ProductionStub.answersOk();
        JsonNode started = read(postAs(ADMIN_OF_A, "/api/quality/batches/" + batch + "/controls").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()));
        ProductionStub.reset();
        return UUID.fromString(started.path("id").asString());
    }

    void addTest(UUID control, String result) throws Exception {
        postAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/tests", Map.of("criterionName", "Humedad", "expectedValue", "8", "obtainedValue", "7",
                "unit", "%", "testResult", result)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
    }

    // ---- base de datos ----

    <T> T control(UUID id, String column, Class<T> type) {
        return jdbc.sql("SELECT " + column + " FROM quality_control WHERE id = :id").param("id", id).query((rs, n) -> rs.getObject(1, type)).list().get(0);
    }

    <T> T failure(UUID id, String column, Class<T> type) {
        return jdbc.sql("SELECT " + column + " FROM quality_failure WHERE id = :id").param("id", id).query((rs, n) -> rs.getObject(1, type)).list().get(0);
    }

    <T> T notice(UUID failureId, UUID batchId, String column, Class<T> type) {
        return jdbc.sql("SELECT " + column + " FROM impacted_batch WHERE failure_id = :f AND batch_id = :b").param("f", failureId).param("b", batchId)
                .query((rs, n) -> rs.getObject(1, type)).list().get(0);
    }

    java.time.Instant at(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    List<Map<String, Object>> audit() {
        return jdbc.sql("SELECT action, user_id, company_id, entity_id FROM audit_log ORDER BY occurred_at, action").query().listOfRows();
    }

    List<String> auditActions() {
        return audit().stream().map(row -> (String) row.get("action")).toList();
    }

    /** Los eventos del outbox: tipo, tópico, clave y payload (sin el sobre). */
    record Outboxed(String type, String topic, String key, JsonNode payload) {
    }

    List<Outboxed> outbox() {
        return jdbc.sql("SELECT event_type, topic, aggregate_id, payload::text AS payload FROM outbox ORDER BY created_at").query((rs, n) ->
                new Outboxed(rs.getString("event_type"), rs.getString("topic"), rs.getString("aggregate_id"), json.readTree(rs.getString("payload")).path("payload"))).list();
    }

    List<String> outboxTypes() {
        return outbox().stream().map(Outboxed::type).toList();
    }

    UUID failureOfBatch(UUID batch) {
        return jdbc.sql("SELECT id FROM quality_failure WHERE batch_id = :b ORDER BY created_at DESC LIMIT 1").param("b", batch).query(UUID.class).single();
    }

    // ---- Production simulado ----

    List<LoggedRequest> sent(UUID batch) {
        return ProductionStub.requestsFor(batch);
    }

    JsonNode sentBody(LoggedRequest request) {
        return json.readTree(request.getBodyAsString());
    }
}
