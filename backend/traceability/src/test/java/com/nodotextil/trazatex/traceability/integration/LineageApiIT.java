package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyRegisteredUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.nodotextil.trazatex.traceability.integration.GraphBuilder.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La API de consultas (US30–US32, US36) como la llama el Gateway: con las cabeceras {@code X-User-Id}, {@code X-Company-Id} y {@code X-User-Role}, sobre
 * un grafo armado por los mismos casos de uso que usan los consumidores y con Neo4j real. Cubre quién puede consultar, qué ve cada empresa en el JSON,
 * el mismo 404 en todos los casos y {@code Cache-Control: no-store} en toda respuesta.
 */
class LineageApiIT extends IntegrationTestBase {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    /** Una empresa que el grafo no conoce (sin lotes ni nodo): p. ej. una inactiva o recién creada. */
    static final UUID NEWCOMER = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    record Caller(UUID userId, UUID companyId, String role) {
    }

    static final Caller OPERATOR_OF_A = new Caller(UUID.fromString("00000000-0000-0000-0000-00000000a0a1"), A, "OPERATOR");
    static final Caller ADMIN_OF_A = new Caller(UUID.fromString("00000000-0000-0000-0000-00000000a0ad"), A, "COMPANY_ADMIN");
    static final Caller OPERATOR_OF_B = new Caller(UUID.fromString("00000000-0000-0000-0000-00000000b0a1"), B, "OPERATOR");
    static final Caller OPERATOR_OF_NEWCOMER = new Caller(UUID.fromString("00000000-0000-0000-0000-00000000c0a1"), NEWCOMER, "OPERATOR");
    static final Caller LICENSE_OWNER = new Caller(UUID.fromString("00000000-0000-0000-0000-0000000000f0"), null, "LICENSE_OWNER");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    RegisterBatchUseCase register;
    @Autowired
    ApplyBatchSplitUseCase split;
    @Autowired
    ApplyTransformationCompletedUseCase transformation;
    @Autowired
    ApplyBatchPhaseChangedUseCase phase;
    @Autowired
    ApplyTransferAcceptedUseCase transfer;
    @Autowired
    ApplyCompanyRegisteredUseCase company;
    @Autowired
    ApplyCompanyPrivacyUseCase privacy;

    GraphBuilder graph;

    @BeforeEach
    void builder() {
        graph = new GraphBuilder(register, split, transformation, phase, transfer, company, privacy);
        graph.company(A, "Hilandería Andina SAC").company(B, "Tintorería del Sur SAC");
    }

    // ---- peticiones ----

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Caller caller) {
        MockHttpServletRequestBuilder builder = request.header("X-User-Id", caller.userId().toString()).header("X-User-Role", caller.role());
        return caller.companyId() == null ? builder : builder.header("X-Company-Id", caller.companyId().toString());
    }

    private org.springframework.test.web.servlet.ResultActions getAs(Caller caller, String url) throws Exception {
        return mockMvc.perform(as(get(url), caller));
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String ancestors(String name) {
        return "/api/traceability/batches/" + id(name) + "/ancestors";
    }

    private static String descendants(String name) {
        return "/api/traceability/batches/" + id(name) + "/descendants";
    }

    private static String lineage(String name) {
        return "/api/traceability/batches/" + id(name) + "/lineage";
    }

    private static final String NO_STORE = "no-store";

    // ---- quién puede consultar ----

    /** Las cuatro rutas de consulta, con un lote que existe (para que un 200 no dependa de nada más). */
    static Stream<String> everyRoute() {
        return Stream.of("/api/traceability/lineages?qrCode=QR-P1", ancestors("P1"), descendants("P1"), lineage("P1"));
    }

    @ParameterizedTest
    @MethodSource("everyRoute")
    void withoutTheIdentityHeadersTheAnswerIs401AndNothingIsRevealed(String url) throws Exception {
        graph.batch("P1", A);

        mockMvc.perform(get(url)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().string("Cache-Control", NO_STORE)).andExpect(jsonPath("$.traceabilityId").doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("everyRoute")
    void anInconsistentOrBrokenIdentityIs401(String url) throws Exception {
        graph.batch("P1", A);
        String user = OPERATOR_OF_A.userId().toString();

        mockMvc.perform(get(url).header("X-User-Id", user).header("X-Company-Id", A.toString())).andExpect(status().isUnauthorized()); // sin rol
        mockMvc.perform(get(url).header("X-User-Role", "OPERATOR").header("X-Company-Id", A.toString())).andExpect(status().isUnauthorized()); // sin usuario
        mockMvc.perform(get(url).header("X-User-Id", user).header("X-User-Role", "OPERATOR")).andExpect(status().isUnauthorized()); // operario sin empresa
        mockMvc.perform(get(url).header("X-User-Id", user).header("X-User-Role", "LICENSE_OWNER").header("X-Company-Id", A.toString()))
                .andExpect(status().isUnauthorized()); // dueño con empresa
        mockMvc.perform(get(url).header("X-User-Id", "no-es-uuid").header("X-User-Role", "OPERATOR").header("X-Company-Id", A.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).header("X-User-Id", user).header("X-User-Role", "SUPERUSUARIO").header("X-Company-Id", A.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).header("X-User-Id", user).header("X-User-Role", "OPERATOR").header("X-Company-Id", "no-es-uuid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theIdentityIsCheckedBeforeAnythingElseSoABrokenRequestWithoutIdentityIsNot400Nor404() throws Exception {
        mockMvc.perform(get("/api/traceability/batches/no-es-uuid/lineage")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/traceability/batches/" + UUID.randomUUID() + "/lineage")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/traceability/lineages?page=-1")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("everyRoute")
    void theLicenseOwnerHasNoCompanyAndIsForbidden(String url) throws Exception {
        graph.batch("P1", A);

        getAs(LICENSE_OWNER, url).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(header().string("Cache-Control", NO_STORE)).andExpect(jsonPath("$.traceabilityId").doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("everyRoute")
    void bothCompanyRolesMayQueryAndBelongToAnyCompanyEvenOneTheGraphDoesNotKnow(String url) throws Exception {
        graph.batch("P1", A);

        for (Caller caller : List.of(OPERATOR_OF_A, ADMIN_OF_A, OPERATOR_OF_B, OPERATOR_OF_NEWCOMER)) {
            getAs(caller, url).andExpect(status().isOk()).andExpect(header().string("Cache-Control", NO_STORE));
        }
    }

    @Test
    void aUserOfAnInactiveCompanyCanQueryBecauseTraceabilityNeverLooksAtTheStateOfACompany() throws Exception {
        // Traceability no guarda el estado de las empresas (ignora CompanyStatusChanged, que es de production; lo prueba KafkaConsumersIT): consultar es
        // lectura. Una empresa inactiva es, para este servicio, una empresa cualquiera: aquí, una que ni siquiera tiene nodo, con su propio lote y los ajenos
        graph.batch("P1", A);
        graph.chain("C", NEWCOMER, 3);

        getAs(OPERATOR_OF_NEWCOMER, "/api/traceability/lineages?traceabilityId=TT-C").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        getAs(OPERATOR_OF_NEWCOMER, ancestors("C2")).andExpect(status().isOk()).andExpect(jsonPath("$.nodes.length()").value(3));
        getAs(OPERATOR_OF_NEWCOMER, ancestors("P1")).andExpect(status().isOk()).andExpect(jsonPath("$.nodes[0].access").value("SHARED"));
    }

    // ---- qué ve cada empresa ----

    @Test
    void theOwnCompanyGetsEveryFieldInTheJsonAndAnotherOnlyTheAlwaysSharedOnes() throws Exception {
        graph.batch("P1", A).phase("P1", A, BatchPhase.AVAILABLE, true, 3).split("P1", A, "H1");

        JsonNode own = body(getAs(OPERATOR_OF_A, ancestors("H1")));
        JsonNode foreign = body(getAs(OPERATOR_OF_B, ancestors("H1")));

        JsonNode ownParent = node(own, "P1");
        assertThat(ownParent.get("access").asString()).isEqualTo("OWN");
        assertThat(ownParent.get("businessName").asString()).isEqualTo("Hilandería Andina SAC");
        assertThat(ownParent.get("traceabilityId").asString()).isEqualTo("TT-P1");
        assertThat(ownParent.get("qrCode").asString()).isEqualTo("QR-P1");
        assertThat(ownParent.get("phase").asString()).isEqualTo("AVAILABLE");
        assertThat(ownParent.get("qualityStatus").asString()).isEqualTo("NOT_REVIEWED");
        assertThat(ownParent.get("createdAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        assertThat(ownParent.get("finalProduct").asBoolean()).isFalse();
        assertThat(ownParent.get("supplierName").asString()).isEqualTo("Textiles del Norte");
        assertThat(ownParent.get("origin").asString()).isEqualTo("Piura");
        assertThat(ownParent.get("quantityKg").decimalValue()).isEqualByComparingTo("100");
        assertThat(ownParent.get("composition").get(0).get("name").asString()).isEqualTo("Algodón");
        assertThat(ownParent.get("receptionCharacteristics").asString()).isEqualTo("Fibra limpia");
        assertThat(ownParent.get("qualityHold").asBoolean()).as("la retención por calidad la ve su empresa").isTrue();

        JsonNode foreignParent = node(foreign, "P1");
        assertThat(foreignParent.get("access").asString()).isEqualTo("SHARED");
        assertThat(foreignParent.get("traceabilityId").asString()).isEqualTo("TT-P1");
        assertThat(foreignParent.get("businessName").asString()).isEqualTo("Hilandería Andina SAC");
        for (String hidden : List.of("supplierName", "origin", "quantityKg", "composition", "receptionCharacteristics", "qualityHold", "transferHistory")) {
            assertThat(foreignParent.has(hidden)).as("%s no sale en el JSON de otra empresa (ni siquiera como null)", hidden).isFalse();
        }
        assertThat(foreign.get("edges")).as("las relaciones siempre se muestran").hasSize(1);
        assertThat(foreign.toString()).as("ni el valor de lo oculto aparece en ningún lado").doesNotContain("Textiles del Norte", "Piura", "Fibra limpia");
    }

    private static JsonNode node(JsonNode lineage, String name) {
        for (JsonNode node : lineage.get("nodes")) {
            if (node.get("id").asString().equals(id(name).toString())) {
                return node;
            }
        }
        throw new AssertionError("el nodo " + name + " no está en la respuesta");
    }

    @Test
    void theCompleteViewCarriesTheTransferHistoryOfEveryBatchForEveryoneAndTheOthersViewsDoNot() throws Exception {
        graph.batch("P1", A).split("P1", A, "H1", "H2").transferred("TR1", "H1", B);

        JsonNode complete = body(getAs(OPERATOR_OF_NEWCOMER, lineage("P1")));

        assertThat(node(complete, "H1").get("transferHistory")).hasSize(1);
        assertThat(node(complete, "H1").get("transferHistory").get(0).get("toBusinessName").asString()).isEqualTo("Tintorería del Sur SAC");
        assertThat(node(complete, "H2").get("transferHistory")).as("sin transferencias: lista vacía, no ausente").isEmpty();
        assertThat(node(complete, "H2").has("transferHistory")).isTrue();
        assertThat(node(body(getAs(OPERATOR_OF_A, ancestors("H1"))), "H1").has("transferHistory")).isFalse();
        assertThat(node(body(getAs(OPERATOR_OF_A, descendants("P1"))), "H1").has("transferHistory")).isFalse();
    }

    @Test
    void theShapeOfALineageResponse() throws Exception {
        graph.batch("P1", A).split("P1", A, "H1");

        getAs(OPERATOR_OF_A, descendants("P1")).andExpect(status().isOk()).andExpect(jsonPath("$.batchId").value(id("P1").toString()))
                .andExpect(jsonPath("$.direction").value("DESCENDANTS")).andExpect(jsonPath("$.maxLevels").value(10)).andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.truncated").value(false)).andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.edges[0].type").value("SPLIT_INTO")).andExpect(jsonPath("$.edges[0].from").value(id("P1").toString()))
                .andExpect(jsonPath("$.edges[0].to").value(id("H1").toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith("application/json"));
    }

    // ---- 404: el mismo cuerpo, sin pistas ----

    @Test
    void anUnknownAProvisionalAnAnIncompleteAndAnUnreadableBatchAnswerExactlyTheSame404() throws Exception {
        graph.transform("T", A, List.of("FANTASMA"), "Z"); // FANTASMA solo existe como entrada provisional
        graph.batch("SIN-FASE", A).batch("FASE-RARA", A);
        neo4j.query("MATCH (b:Batch {batchId: $id}) REMOVE b.phase").bind(id("SIN-FASE").toString()).to("id").run();
        neo4j.query("MATCH (b:Batch {batchId: $id}) SET b.phase = 'FLOTANDO'").bind(id("FASE-RARA").toString()).to("id").run();
        UUID unknown = UUID.randomUUID();

        for (String route : List.of("ancestors", "descendants", "lineage")) {
            List<JsonNode> answers = new ArrayList<>();
            for (UUID batch : List.of(unknown, id("FANTASMA"), id("SIN-FASE"), id("FASE-RARA"))) {
                for (Caller caller : List.of(OPERATOR_OF_A, OPERATOR_OF_B, OPERATOR_OF_NEWCOMER)) {
                    MvcResult result = mockMvc.perform(as(get("/api/traceability/batches/" + batch + "/" + route), caller)).andExpect(status().isNotFound())
                            .andExpect(header().string("Cache-Control", NO_STORE)).andReturn();
                    answers.add(json.readTree(result.getResponse().getContentAsString()));
                }
            }
            JsonNode first = answers.getFirst();
            assertThat(first.get("code").asString()).isEqualTo("BATCH_NOT_FOUND");
            assertThat(first.get("message").asString()).isEqualTo("El lote no existe.");
            assertThat(first.get("details")).isEmpty();
            for (JsonNode other : answers) {
                assertThat(other.get("code")).isEqualTo(first.get("code"));
                assertThat(other.get("message")).as("%s: el mensaje es idéntico", route).isEqualTo(first.get("message"));
                assertThat(other.get("details")).isEqualTo(first.get("details"));
                assertThat(other.propertyNames()).as("los mismos campos").containsExactlyInAnyOrderElementsOf(first.propertyNames());
                assertThat(other.toString()).as("ni el id pedido ni 'provisional' ni 'incompleto' aparecen").doesNotContain(unknown.toString(), id("FANTASMA").toString(),
                        id("SIN-FASE").toString(), id("FASE-RARA").toString(), "provision", "incomplet");
            }
        }
    }

    // ---- 400 ----

    @Test
    void aBatchIdThatIsNotAUuidIs400LikeEveryOtherMalformedParameter() throws Exception {
        for (String route : List.of("ancestors", "descendants", "lineage")) {
            getAs(OPERATOR_OF_A, "/api/traceability/batches/no-es-uuid/" + route).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER")).andExpect(jsonPath("$.details[0]").value("batchId"))
                    .andExpect(header().string("Cache-Control", NO_STORE));
        }
    }

    @Test
    void theSearchValidatesItsParameters() throws Exception {
        graph.batch("P1", A);

        getAs(OPERATOR_OF_A, "/api/traceability/lineages").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_SEARCH_CRITERIA"));
        // los espacios van como parámetro (en la URL, MockMvc volvería a codificar un %20)
        mockMvc.perform(as(get("/api/traceability/lineages").queryParam("qrCode", "").queryParam("traceabilityId", "   "), OPERATOR_OF_A))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_SEARCH_CRITERIA"));
        getAs(OPERATOR_OF_A, "/api/traceability/lineages?qrCode=QR-P1&page=-1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        getAs(OPERATOR_OF_A, "/api/traceability/lineages?qrCode=QR-P1&size=0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        getAs(OPERATOR_OF_A, "/api/traceability/lineages?qrCode=QR-P1&page=abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER")).andExpect(jsonPath("$.details[0]").value("page"));
        getAs(OPERATOR_OF_A, "/api/traceability/lineages?qrCode=QR-P1&size=1000").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    // ---- búsqueda ----

    @Test
    void theSearchFindsByQrAnywhereAndByTraceabilityIdPrefixOnlyAmongTheOwnBatches() throws Exception {
        graph.batches(A, "AAA1", "AAA2").batches(B, "AAA9");

        getAs(OPERATOR_OF_A, "/api/traceability/lineages?qrCode=QR-AAA9").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].traceabilityId").value("TT-AAA9")).andExpect(jsonPath("$.items[0].access").value("SHARED"))
                .andExpect(jsonPath("$.items[0].businessName").value("Tintorería del Sur SAC")).andExpect(jsonPath("$.items[0].batchId").value(id("AAA9").toString()));
        getAs(OPERATOR_OF_A, "/api/traceability/lineages?traceabilityId=TT-AAA").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[*].traceabilityId").value(org.hamcrest.Matchers.containsInAnyOrder("TT-AAA1", "TT-AAA2")));
        getAs(OPERATOR_OF_A, "/api/traceability/lineages?traceabilityId=TT-AAA9").andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].traceabilityId").value("TT-AAA9"));
        getAs(OPERATOR_OF_A, "/api/traceability/lineages?traceabilityId=TT-AAA&qrCode=QR-AAA9").andExpect(jsonPath("$.totalElements").value(0));
        getAs(ADMIN_OF_A, "/api/traceability/lineages?traceabilityId=tt-aaa").andExpect(jsonPath("$.totalElements").value(2)); // normaliza lo escrito a mano
    }

    @Test
    void theSearchIsPaginatedAndEveryPageSaysWhereItIs() throws Exception {
        for (int i = 0; i < 25; i++) {
            graph.batch(String.format("N%02d", i), A);
        }

        getAs(OPERATOR_OF_A, "/api/traceability/lineages?traceabilityId=TT-N&size=10&page=2").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.size").value(10)).andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));
        getAs(OPERATOR_OF_A, "/api/traceability/lineages?traceabilityId=TT-N").andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.items[0].traceabilityId").value("TT-N24"));
    }

    @Test
    void hostileTextInTheQueryStringIsJustText() throws Exception {
        graph.batch("P1", A);

        for (String text : List.of("QR'\"}) RETURN 1 //", "\"}) MATCH (n) DETACH DELETE n //", "'; DROP CONSTRAINT batch_id_unique; //")) {
            mockMvc.perform(as(get("/api/traceability/lineages").queryParam("qrCode", text), OPERATOR_OF_A)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
            mockMvc.perform(as(get("/api/traceability/lineages").queryParam("traceabilityId", text), OPERATOR_OF_A)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
        getAs(OPERATOR_OF_A, "/api/traceability/lineages?qrCode=QR-P1").andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---- name: el nombre libre del lote sale solo a la empresa dueña (OWN), en todas las salidas ----

    static final String NAME_OF_A = "Pedido urgente Andina";
    static final String NAME_OF_A_OUTPUT = "Salida teñida Andina";
    static final String NAME_OF_B = "Pedido urgente Tintorería";

    /**
     * A registra NA y B registra NB, ambos con nombre; A los mezcla en TX y obtiene NX, también con nombre. Con {@code privacy} = {@code "privado"} ninguna
     * empresa configuró nada; con {@code "compartido"} las dos comparten todos los campos configurables.
     */
    private void namedBatchesOfTwoCompanies(String privacy) {
        graph.named("NA", NAME_OF_A).named("NB", NAME_OF_B).named("NX", NAME_OF_A_OUTPUT)
                .batch("NA", A).batch("NB", B).transform("TX", A, List.of("NA", "NB"), "NX");
        if (privacy.equals("compartido")) {
            java.util.Map<String, String> everything = new java.util.LinkedHashMap<>();
            for (com.nodotextil.trazatex.traceability.domain.model.ConfigurableField field : com.nodotextil.trazatex.traceability.domain.model.ConfigurableField.values()) {
                everything.put(field.name(), "SHARED");
            }
            graph.privacy(A, everything).privacy(B, everything);
        }
    }

    private static void assertNamedOnlyIfOwn(JsonNode node, String expectedName, String context) {
        if (node.get("access").asString().equals("OWN")) {
            assertThat(node.has("name") ? node.get("name").asString() : null).as("%s: OWN lleva su nombre", context).isEqualTo(expectedName);
        } else {
            assertThat(node.has("name")).as("%s: SHARED nunca lleva name", context).isFalse();
        }
    }

    /** Hacia atrás (US31), hacia adelante (US32) y completa (US36), vistas por la dueña (OWN) y por la otra empresa (SHARED), privado o compartido. */
    @ParameterizedTest(name = "configuración: {0}")
    @ValueSource(strings = {"privado", "compartido"})
    void theBatchNameAppearsOnlyInOwnNodesOfEveryGenealogyView(String privacy) throws Exception {
        namedBatchesOfTwoCompanies(privacy);

        for (String url : List.of(ancestors("NX"), lineage("NX"), descendants("NB"), lineage("NB"), descendants("NA"))) {
            JsonNode seenByA = body(getAs(OPERATOR_OF_A, url).andExpect(status().isOk()));
            JsonNode seenByB = body(getAs(OPERATOR_OF_B, url).andExpect(status().isOk()));
            String context = url + " (" + privacy + ")";

            for (JsonNode view : List.of(seenByA, seenByB)) {
                for (JsonNode node : view.get("nodes")) {
                    if (node.get("kind").asString().equals("BATCH")) {
                        String key = node.get("id").asString();
                        String expected = key.equals(id("NA").toString()) ? NAME_OF_A : key.equals(id("NB").toString()) ? NAME_OF_B : NAME_OF_A_OUTPUT;
                        assertNamedOnlyIfOwn(node, expected, context);
                    }
                }
            }
            assertThat(seenByA.toString()).as("%s: A no recibe el nombre de B en ningún lugar", context).doesNotContain(NAME_OF_B);
            assertThat(seenByB.toString()).as("%s: B no recibe los nombres de A en ningún lugar", context).doesNotContain(NAME_OF_A, NAME_OF_A_OUTPUT);
        }
        // control: la prueba mira nodos de los dos tipos de acceso y, con "compartido", lo configurable sí sale
        JsonNode foreignForA = node(body(getAs(OPERATOR_OF_A, ancestors("NX"))), "NB");
        assertThat(foreignForA.get("access").asString()).isEqualTo("SHARED");
        assertThat(foreignForA.has("supplierName")).isEqualTo(privacy.equals("compartido"));
        assertThat(node(body(getAs(OPERATOR_OF_A, ancestors("NX"))), "NA").get("name").asString()).isEqualTo(NAME_OF_A);
        assertThat(node(body(getAs(OPERATOR_OF_B, descendants("NB"))), "NB").get("name").asString()).isEqualTo(NAME_OF_B);
    }

    /** Búsqueda por QR (US30, cualquier empresa) y por Traceability ID (prefijo propio o exacto ajeno): el resumen no lleva name, ni propio ni ajeno. */
    @ParameterizedTest(name = "configuración: {0}")
    @ValueSource(strings = {"privado", "compartido"})
    void noSearchResultCarriesTheBatchNameNeitherOwnNorShared(String privacy) throws Exception {
        namedBatchesOfTwoCompanies(privacy);
        java.util.Set<String> accesses = new java.util.TreeSet<>();

        for (Caller caller : List.of(OPERATOR_OF_A, OPERATOR_OF_B)) {
            for (String query : List.of("qrCode=QR-NA", "qrCode=QR-NB", "qrCode=QR-NX", "traceabilityId=TT-NA", "traceabilityId=TT-NB", "traceabilityId=TT-NX",
                    "traceabilityId=TT-")) {
                JsonNode page = body(getAs(caller, "/api/traceability/lineages?" + query).andExpect(status().isOk()));
                String context = caller.companyId() + " " + query + " (" + privacy + ")";
                assertThat(page.get("items").size()).as("%s: la búsqueda encuentra algo", context).isPositive();
                for (JsonNode item : page.get("items")) {
                    assertThat(item.has("name")).as(context).isFalse();
                    accesses.add(item.get("access").asString());
                }
                assertThat(page.toString()).as(context).doesNotContain(NAME_OF_A, NAME_OF_A_OUTPUT, NAME_OF_B);
            }
        }
        assertThat(accesses).as("se probaron resultados propios y ajenos").containsExactly("OWN", "SHARED");
    }

    // ---- Cache-Control: no-store en toda respuesta ----

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE", "PATCH"})
    void everyResponseIsNotCacheableWhateverTheOutcome(String method) throws Exception {
        graph.batch("P1", A);
        HttpMethod http = HttpMethod.valueOf(method);
        List<String> urls = List.of("/api/traceability/lineages?qrCode=QR-P1", ancestors("P1"), descendants("P1"), lineage("P1"), // 200 (GET) o 405
                "/api/traceability/batches/" + UUID.randomUUID() + "/lineage", // 404
                "/api/traceability/batches/no-es-uuid/lineage", // 400
                "/api/traceability/lineages", // 400
                "/api/traceability/no-existe", "/api/traceability", "/api/traceability/", "/api/traceability/batches/" + id("P1")); // 404 de ruta o 405
        for (Caller caller : new Caller[]{OPERATOR_OF_A, LICENSE_OWNER, null}) {
            for (String url : urls) {
                MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(http, url);
                mockMvc.perform(caller == null ? request : as(request, caller)).andExpect(header().string("Cache-Control", NO_STORE));
            }
        }
    }

    @Test
    void theNoStoreRuleCoversOnlyTheTraceabilityRoutes() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(header().doesNotExist("Cache-Control"));
        mockMvc.perform(get("/api/traceabilityX")).andExpect(header().doesNotExist("Cache-Control"));
    }
}
