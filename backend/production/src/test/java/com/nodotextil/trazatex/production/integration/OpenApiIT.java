package com.nodotextil.trazatex.production.integration;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cada servicio expone su OpenAPI (springdoc); aquí se comprueba que describe las rutas de docs/architecture/api.md. */
class OpenApiIT extends ApiTestBase {

    @Test
    void theOpenApiDocumentListsTheProductionRoutes() throws Exception {
        JsonNode docs = json.readTree(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        List<String> paths = new ArrayList<>();
        docs.path("paths").propertyNames().forEach(paths::add);
        assertThat(paths).contains("/api/batches", "/api/batches/{id}", "/api/batches/qr/{qrCode}", "/api/batches/{batchId}/split",
                "/api/batches/{id}/commercial-data", "/api/batches/{id}/evidence", "/api/transformations/{id}/evidence", "/api/machines", "/api/machines/{id}", "/api/machines/{id}/status",
                "/api/transformations", "/api/transformations/{id}", "/api/transformations/{id}/complete", "/api/transfers",
                "/api/transfers/{id}", "/api/transfers/{id}/accept", "/api/transfers/{id}/reject");
    }
}
