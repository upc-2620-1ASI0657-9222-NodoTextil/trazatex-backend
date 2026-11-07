package com.nodotextil.trazatex.apigateway.infrastructure.revocation;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Lee el sobre de {@code UserAccountUpdated} (docs/architecture/communication.md). Es tolerante: solo importan
 * {@code payload.userId} y {@code payload.status}; cualquier campo desconocido se ignora, de modo que organization-access puede
 * agregar campos sin romper al Gateway. Es <b>ilegible</b> únicamente si falta {@code userId} (o no es un UUID) o falta
 * {@code status}.
 *
 * <p>Un {@code status} que no sea {@code ACTIVE} revoca (también uno desconocido): el glosario solo tiene {@code ACTIVE} e
 * {@code INACTIVE}, y un estado nuevo sería casi con seguridad "no activo"; ante la duda se niega el acceso.
 */
final class UserAccountEventParser {

    static final String TYPE = "UserAccountUpdated";
    private static final String ACTIVE = "ACTIVE";

    sealed interface Result permits Update, Ignored, Unreadable {
    }

    /** @param recognized {@code false} si el estado no es ni {@code ACTIVE} ni {@code INACTIVE} (se revoca igual) */
    record Update(UUID userId, String status, boolean inactive, boolean recognized) implements Result {
    }

    /** Evento de otro tipo en el tópico: no es de este consumidor. */
    record Ignored(String type) implements Result {
    }

    record Unreadable(String reason) implements Result {
    }

    private final JsonMapper json;

    UserAccountEventParser(JsonMapper json) {
        this.json = json;
    }

    Result parse(String value) {
        if (value == null || value.isBlank()) {
            return new Unreadable("mensaje vacío");
        }
        JsonNode envelope;
        try {
            envelope = json.readTree(value);
        } catch (JacksonException e) {
            return new Unreadable("no es JSON");
        }
        if (envelope == null || !envelope.isObject()) {
            return new Unreadable("no es un objeto JSON");
        }
        JsonNode type = envelope.get("type");
        if (type != null && type.isString() && !TYPE.equals(type.asString())) {
            return new Ignored(type.asString());
        }
        JsonNode payload = envelope.get("payload");
        if (payload == null || !payload.isObject()) {
            return new Unreadable("falta payload");
        }
        JsonNode userIdNode = payload.get("userId");
        if (userIdNode == null || !userIdNode.isString()) {
            return new Unreadable("falta userId");
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdNode.asString());
        } catch (IllegalArgumentException e) {
            return new Unreadable("userId no es un UUID");
        }
        JsonNode statusNode = payload.get("status");
        if (statusNode == null || !statusNode.isString() || statusNode.asString().isBlank()) {
            return new Unreadable("falta status");
        }
        String status = statusNode.asString();
        boolean recognized = ACTIVE.equals(status) || "INACTIVE".equals(status);
        return new Update(userId, status, !ACTIVE.equals(status), recognized);
    }
}
