package com.nodotextil.trazatex.production.acceptance;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * El estado de un escenario: quién actúa, lo que el escenario ha ido creando y la última respuesta. Vive solo durante el escenario
 * (ámbito {@code cucumber-glue}, sin proxy: ver {@link AcceptanceBeans}); lo comparten los pasos de todas las clases.
 */
public class World {

    public static final UUID COMPANY_A = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    public static final UUID COMPANY_B = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    public final Who operatorA = new Who(UUID.fromString("00000000-0000-0000-0000-00000000a0a1"), "OPERATOR", COMPANY_A);
    public final Who adminA = new Who(UUID.fromString("00000000-0000-0000-0000-00000000a0ad"), "COMPANY_ADMIN", COMPANY_A);
    public final Who operatorB = new Who(UUID.fromString("00000000-0000-0000-0000-00000000b0a1"), "OPERATOR", COMPANY_B);
    public final Who adminB = new Who(UUID.fromString("00000000-0000-0000-0000-00000000b0ad"), "COMPANY_ADMIN", COMPANY_B);

    /** Lo que el escenario va a enviar cuando el usuario "confirma": los pasos {@code Dado} lo preparan y los {@code Cuando} lo ejecutan. */
    public Supplier<Reply> pending;
    public Reply last;
    public final List<Reply> replies = new ArrayList<>();
    /** Ventana de tiempo de la última petición, para comprobar "fecha y hora" con el reloj real. */
    public Instant sentAt;
    public Instant answeredAt;

    // Lo que el escenario ha creado
    public JsonNode batch;
    public final List<JsonNode> batches = new ArrayList<>();
    public JsonNode machine;
    public final List<JsonNode> machines = new ArrayList<>();
    public JsonNode transformation;
    public JsonNode transfer;
    public Map<String, Object> body = new LinkedHashMap<>();
    public final Map<String, Object> notes = new HashMap<>();

    /** Ejecuta la petición pendiente y guarda su respuesta y la ventana de tiempo en que ocurrió. */
    public Reply submit() {
        if (pending == null) {
            throw new IllegalStateException("El escenario no preparó ninguna petición para confirmar");
        }
        return send(pending);
    }

    public Reply send(Supplier<Reply> request) {
        sentAt = Instant.now();
        last = request.get();
        answeredAt = Instant.now();
        replies.add(last);
        return last;
    }

    public UUID id(JsonNode node) {
        return UUID.fromString(node.path("id").asString());
    }
}
