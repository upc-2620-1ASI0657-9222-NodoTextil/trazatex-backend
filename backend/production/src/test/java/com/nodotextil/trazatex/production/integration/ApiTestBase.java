package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base de las pruebas de la API: peticiones como las manda el Gateway (cabeceras {@code X-User-*}/{@code X-Company-Id}),
 * dos empresas {@code ACTIVE} en la proyección y un operario y un administrador de cada una.
 */
abstract class ApiTestBase extends IntegrationTestBase {

    static final UUID COMPANY_A = COMPANY;
    static final UUID COMPANY_B = OTHER_COMPANY;
    static final UUID OPERATOR_A = UUID.fromString("00000000-0000-0000-0000-00000000a0a1");
    static final UUID ADMIN_A = UUID.fromString("00000000-0000-0000-0000-00000000a0ad");
    static final UUID OPERATOR_B = UUID.fromString("00000000-0000-0000-0000-00000000b0a1");
    static final UUID ADMIN_B = UUID.fromString("00000000-0000-0000-0000-00000000b0ad");
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f0");

    /** Quien llama: usuario, empresa y rol, como los inyecta el Gateway. */
    record Caller(UUID userId, UUID companyId, String role) {
    }

    static final Caller OPERATOR_OF_A = new Caller(OPERATOR_A, COMPANY_A, "OPERATOR");
    static final Caller ADMIN_OF_A = new Caller(ADMIN_A, COMPANY_A, "COMPANY_ADMIN");
    static final Caller OPERATOR_OF_B = new Caller(OPERATOR_B, COMPANY_B, "OPERATOR");
    static final Caller ADMIN_OF_B = new Caller(ADMIN_B, COMPANY_B, "COMPANY_ADMIN");
    static final Caller LICENSE_OWNER = new Caller(OWNER, null, "LICENSE_OWNER");

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper json;

    @BeforeEach
    void seedCompanies() {
        saveCompany(COMPANY_A, CompanyStatus.ACTIVE);
        saveCompany(COMPANY_B, CompanyStatus.ACTIVE);
    }

    // ---- peticiones ----

    protected static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Caller caller) {
        MockHttpServletRequestBuilder builder = request.header("X-User-Id", caller.userId().toString()).header("X-User-Role", caller.role());
        return caller.companyId() == null ? builder : builder.header("X-Company-Id", caller.companyId().toString());
    }

    protected ResultActions call(Caller caller, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(as(request, caller));
    }

    protected ResultActions getAs(Caller caller, String url) throws Exception {
        return call(caller, get(url));
    }

    protected ResultActions postAs(Caller caller, String url, Object body) throws Exception {
        return call(caller, post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected ResultActions postAs(Caller caller, String url) throws Exception {
        return call(caller, post(url));
    }

    protected ResultActions patchAs(Caller caller, String url, Object body) throws Exception {
        return call(caller, patch(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected ResultActions putAs(Caller caller, String url, Object body) throws Exception {
        return call(caller, put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    /** El comando interno de Quality: sin identidad, sin roles y sin Idempotency-Key (no está bajo /api). */
    protected ResultActions internalCommand(UUID batchId, Object body) throws Exception {
        return mockMvc.perform(post("/internal/batches/" + batchId + "/phase-transitions").contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String text ? text : json.writeValueAsString(body)));
    }

    /** Cuerpo del comando interno con un requestedBy fijo. */
    protected static Map<String, Object> phaseCommand(String operation, String requestId) {
        return obj("operation", operation, "requestId", requestId, "requestedBy", OPERATOR_A);
    }

    protected static Map<String, Object> phaseCommand(String operation, String requestId, Boolean qualityHold, String reason) {
        return obj("operation", operation, "requestId", requestId, "qualityHold", qualityHold, "reason", reason, "requestedBy", OPERATOR_A);
    }

    /** Una subida multipart (campo "files") como la manda el Gateway: con las cabeceras de identidad. */
    protected ResultActions multipartAs(Caller caller, String url, org.springframework.mock.web.MockMultipartFile... files) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(url);
        for (var file : files) {
            request.file(file);
        }
        request.header("X-User-Id", caller.userId().toString()).header("X-User-Role", caller.role());
        if (caller.companyId() != null) {
            request.header("X-Company-Id", caller.companyId().toString());
        }
        return mockMvc.perform(request);
    }

    protected ResultActions postWithKey(Caller caller, String url, Object body, String key) throws Exception {
        return mockMvc.perform(as(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))
                .header("Idempotency-Key", key), caller));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected String code(ResultActions result) throws Exception {
        return read(result).path("code").asString();
    }

    /** Un objeto JSON a partir de pares clave-valor; admite nulos (a diferencia de {@code Map.of}). */
    protected static Map<String, Object> obj(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    // ---- datos de partida, por la propia API ----

    protected static Map<String, Object> component(String name, double percentage) {
        return obj("name", name, "percentage", percentage);
    }

    protected Map<String, Object> batchBody(double kg) {
        return obj("name", "Algodón Pima", "materialType", "Fibra",
                "composition", List.of(component("Algodón", 70), component("Poliéster", 30)),
                "quantityKg", kg, "origin", "Piura", "supplierName", "Textiles del Norte", "receptionCharacteristics", "Fibra limpia");
    }

    /** Registra un lote como operario de A por la API y devuelve su respuesta. */
    protected JsonNode registerBatch(double kg) throws Exception {
        return read(postAs(OPERATOR_OF_A, "/api/batches", batchBody(kg)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()));
    }

    protected JsonNode registerBatchAs(Caller caller, double kg) throws Exception {
        return read(postAs(caller, "/api/batches", batchBody(kg)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()));
    }

    protected JsonNode registerMachine(String code) throws Exception {
        return read(postAs(ADMIN_OF_A, "/api/machines", obj("internalCode", code, "name", "Cardadora", "type", "Cardado"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()));
    }

    /** Inicia una transformación como operario de A por la API. */
    protected JsonNode startTransformation(String type, UUID machineId, UUID... batchIds) throws Exception {
        return read(postAs(OPERATOR_OF_A, "/api/transformations", obj("type", type, "machineId", machineId,
                "batchIds", List.of(batchIds))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()));
    }

    protected static Map<String, Object> output(String materialType, double kg) {
        return obj("materialType", materialType, "quantityKg", kg);
    }

    protected UUID idOf(JsonNode node) {
        return UUID.fromString(node.path("id").asString());
    }
}
