package com.nodotextil.trazatex.quality.acceptance;

import tools.jackson.databind.JsonNode;

public record Reply(int status, JsonNode body, String raw, java.util.Map<String, String> headers, byte[] bytes) {

    public Reply(int status, JsonNode body, String raw) {
        this(status, body, raw, java.util.Map.of(), new byte[0]);
    }

    public String header(String name) {
        return headers.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), "");
    }

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
