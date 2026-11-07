package com.nodotextil.trazatex.traceability.acceptance;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/** Lo que los escenarios hacen con la API: consultarla como la llama el Gateway (cabeceras de identidad de un operario de la empresa dada). */
public class Api {

    /** La respuesta HTTP: estado, cuerpo JSON y la cabecera {@code Cache-Control}. */
    public record Reply(int status, JsonNode body, String cacheControl) {
    }

    private static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-00000000a0a1");

    private final MockMvc mockMvc;
    private final ObjectMapper json;

    Api(MockMvc mockMvc, ObjectMapper json) {
        this.mockMvc = mockMvc;
        this.json = json;
    }

    /** {@code GET url} como un OPERATOR de la empresa. */
    public Reply get(String url, UUID companyId) {
        try {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(url).header("X-User-Id", OPERATOR.toString()).header("X-Company-Id", companyId.toString())
                    .header("X-User-Role", "OPERATOR")).andReturn();
            String body = result.getResponse().getContentAsString();
            return new Reply(result.getResponse().getStatus(), body.isBlank() ? null : json.readTree(body), result.getResponse().getHeader("Cache-Control"));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo consultar " + url, e);
        }
    }
}
