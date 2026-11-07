package com.nodotextil.trazatex.production.acceptance;

import tools.jackson.databind.JsonNode;

/** Una respuesta HTTP real del servicio. {@code body} es el JSON (nulo si no hubo cuerpo). */
public record Reply(int status, JsonNode body, String raw) {

    public String text(String field) {
        JsonNode node = body == null ? null : body.get(field);
        return node == null || node.isNull() ? "" : node.asString();
    }

    public JsonNode field(String field) {
        return body == null ? null : body.get(field);
    }

    public String code() {
        return text("code");
    }

    public String message() {
        return text("message");
    }
}
