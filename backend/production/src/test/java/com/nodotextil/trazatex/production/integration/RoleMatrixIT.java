package com.nodotextil.trazatex.production.integration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quién puede llamar a cada endpoint: OPERATOR opera, COMPANY_ADMIN gestiona maquinaria y resuelve transferencias, ambos
 * consultan, LICENSE_OWNER (sin empresa) no entra a ninguno (403), y sin identidad válida es 401. Los recursos no existen:
 * lo que se comprueba es la puerta (401/403), no el resultado de la operación.
 */
class RoleMatrixIT extends ApiTestBase {

    private static final String ID = "00000000-0000-0000-0000-00000000dead";
    private static final String OPERATOR = "OPERATOR";
    private static final String ADMIN = "COMPANY_ADMIN";

    /** método, ruta, roles autorizados. */
    private record Endpoint(HttpMethod method, String path, List<String> allowed) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private static final List<String> BOTH = List.of(OPERATOR, ADMIN);

    static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint(HttpMethod.POST, "/api/batches", List.of(OPERATOR)),
            new Endpoint(HttpMethod.GET, "/api/batches", BOTH),
            new Endpoint(HttpMethod.GET, "/api/batches/" + ID, BOTH),
            new Endpoint(HttpMethod.GET, "/api/batches/qr/" + "0".repeat(26), BOTH),
            new Endpoint(HttpMethod.POST, "/api/batches/" + ID + "/split", List.of(OPERATOR)),
            new Endpoint(HttpMethod.PUT, "/api/batches/" + ID + "/commercial-data", List.of(OPERATOR)),
            new Endpoint(HttpMethod.POST, "/api/batches/" + ID + "/evidence", List.of(OPERATOR)),
            new Endpoint(HttpMethod.GET, "/api/batches/" + ID + "/evidence", BOTH),
            new Endpoint(HttpMethod.POST, "/api/transformations/" + ID + "/evidence", List.of(OPERATOR)),
            new Endpoint(HttpMethod.GET, "/api/transformations/" + ID + "/evidence", BOTH),
            new Endpoint(HttpMethod.POST, "/api/machines", List.of(ADMIN)),
            new Endpoint(HttpMethod.GET, "/api/machines", BOTH),
            new Endpoint(HttpMethod.GET, "/api/machines/" + ID, BOTH),
            new Endpoint(HttpMethod.PATCH, "/api/machines/" + ID + "/status", List.of(ADMIN)),
            new Endpoint(HttpMethod.POST, "/api/transformations", List.of(OPERATOR)),
            new Endpoint(HttpMethod.GET, "/api/transformations", BOTH),
            new Endpoint(HttpMethod.GET, "/api/transformations/" + ID, BOTH),
            new Endpoint(HttpMethod.POST, "/api/transformations/" + ID + "/complete", List.of(OPERATOR)),
            new Endpoint(HttpMethod.POST, "/api/transfers", List.of(OPERATOR)),
            new Endpoint(HttpMethod.GET, "/api/transfers", BOTH),
            new Endpoint(HttpMethod.GET, "/api/transfers/" + ID, BOTH),
            new Endpoint(HttpMethod.POST, "/api/transfers/" + ID + "/accept", List.of(ADMIN)),
            new Endpoint(HttpMethod.POST, "/api/transfers/" + ID + "/reject", List.of(ADMIN)));

    static Stream<Arguments> endpoints() {
        return ENDPOINTS.stream().map(Arguments::of);
    }

    private MockHttpServletRequestBuilder request(Endpoint endpoint) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(endpoint.method(), endpoint.path());
        if (endpoint.method() == HttpMethod.GET) {
            return builder;
        }
        // Las evidencias son multipart: con otro tipo el 415 ocurre al elegir el controlador, antes de la puerta de roles
        return endpoint.path().endsWith("/evidence") ? builder.contentType("multipart/form-data; boundary=X").content("--X--")
                : builder.contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    private int statusOf(Endpoint endpoint, Caller caller) throws Exception {
        MvcResult result = call(caller, request(endpoint)).andReturn();
        return result.getResponse().getStatus();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void everyRoleGetsTheRightDoor(Endpoint endpoint) throws Exception {
        for (Caller caller : List.of(OPERATOR_OF_A, ADMIN_OF_A)) {
            int status = statusOf(endpoint, caller);
            if (endpoint.allowed().contains(caller.role())) {
                assertThat(status).as("%s como %s", endpoint, caller.role()).isNotIn(401, 403);
            } else {
                assertThat(status).as("%s como %s", endpoint, caller.role()).isEqualTo(403);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void theLicenseOwnerHasNoCompanyAndEntersNowhere(Endpoint endpoint) throws Exception {
        assertThat(statusOf(endpoint, LICENSE_OWNER)).as("%s como LICENSE_OWNER", endpoint).isEqualTo(403);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void withoutAValidIdentityTheAnswerIs401(Endpoint endpoint) throws Exception {
        // sin cabeceras
        assertThat(mockMvc.perform(request(endpoint)).andReturn().getResponse().getStatus()).as("%s sin identidad", endpoint).isEqualTo(401);
        // identidad inconsistente: un rol de empresa sin empresa, y el Dueño con una empresa
        assertThat(statusOf(endpoint, new Caller(OPERATOR_A, null, OPERATOR))).as("operario sin empresa").isEqualTo(401);
        assertThat(statusOf(endpoint, new Caller(OWNER, COMPANY_A, "LICENSE_OWNER"))).as("Dueño con empresa").isEqualTo(401);
        // valores inválidos
        assertThat(statusOf(endpoint, new Caller(OPERATOR_A, COMPANY_A, "SUPERUSER"))).as("rol desconocido").isEqualTo(401);
        assertThat(mockMvc.perform(request(endpoint).header("X-User-Id", "no-es-uuid").header("X-User-Role", OPERATOR)
                .header("X-Company-Id", COMPANY_A.toString())).andReturn().getResponse().getStatus()).as("X-User-Id inválido").isEqualTo(401);
        assertThat(mockMvc.perform(request(endpoint).header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", OPERATOR)
                .header("X-Company-Id", "no-es-uuid")).andReturn().getResponse().getStatus()).as("X-Company-Id inválido").isEqualTo(401);
        assertThat(mockMvc.perform(request(endpoint).header("X-User-Id", UUID.randomUUID().toString())
                .header("X-Company-Id", COMPANY_A.toString())).andReturn().getResponse().getStatus()).as("sin rol").isEqualTo(401);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void theErrorsOfTheDoorUseTheStandardBody(Endpoint endpoint) throws Exception {
        String forbidden = call(LICENSE_OWNER, request(endpoint)).andReturn().getResponse().getContentAsString();
        assertThat(forbidden).contains("\"code\":\"FORBIDDEN\"").contains("\"timestamp\"");
        String unauthorized = mockMvc.perform(request(endpoint)).andReturn().getResponse().getContentAsString();
        assertThat(unauthorized).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
    }
}
