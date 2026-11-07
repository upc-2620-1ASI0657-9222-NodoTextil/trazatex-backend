package com.nodotextil.trazatex.production.acceptance;

import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo común de las clases de pasos: preparar el escenario <b>por HTTP real</b> (registrar lotes, maquinaria, transformaciones...) y leer lo
 * ocurrido. Los datos de partida pasan por los mismos endpoints que usará el frontend; lo único que no es HTTP es leer el outbox y la
 * base para comprobar lo que el escenario promete (eventos, filas que no deben existir).
 */
abstract class StepSupport {

    protected final World world;
    protected final ProductionApi api;
    protected final Outbox outbox;
    protected final JdbcClient jdbc;
    protected final ObjectMapper json;
    private int machineCounter;

    StepSupport(World world, ProductionApi api, Outbox outbox, JdbcClient jdbc, ObjectMapper json) {
        this.world = world;
        this.api = api;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.json = json;
    }

    // ---- cuerpos ----

    protected static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    protected static Map<String, Object> component(String name, double percentage) {
        return map("name", name, "percentage", percentage);
    }

    /** Un registro de lote completo y válido: composición 70/30, con proveedor. */
    protected static Map<String, Object> lotBody(double kg) {
        return map("name", "Algodón Pima", "materialType", "Fibra", "composition", List.of(component("Algodón", 70), component("Poliéster", 30)),
                "quantityKg", kg, "origin", "Piura", "supplierName", "Textiles del Norte", "receptionCharacteristics", "Fibra limpia");
    }

    // ---- preparar el escenario por HTTP ----

    protected JsonNode registerLot(Map<String, Object> body) {
        Reply reply = api.post(world.operatorA, "/api/batches", body);
        assertThat(reply.status()).as("preparar un lote: %s", reply.raw()).isEqualTo(201);
        world.batches.add(reply.body());
        return reply.body();
    }

    protected JsonNode registerLot(double kg) {
        return registerLot(lotBody(kg));
    }

    protected JsonNode registerMachine() {
        Reply reply = api.post(world.adminA, "/api/machines", map("internalCode", "MQ-" + (++machineCounter) + "-" + UUID.randomUUID().toString().substring(0, 6),
                "name", "Cardadora", "type", "Cardado"));
        assertThat(reply.status()).as("preparar una maquinaria: %s", reply.raw()).isEqualTo(201);
        world.machines.add(reply.body());
        return reply.body();
    }

    protected void setMachineStatus(JsonNode machine, String status) {
        Reply reply = api.patch(world.adminA, "/api/machines/" + id(machine) + "/status", map("status", status));
        assertThat(reply.status()).as("cambiar la maquinaria a %s: %s", status, reply.raw()).isEqualTo(200);
    }

    protected JsonNode startTransformation(String type, JsonNode machine, JsonNode... lots) {
        List<String> ids = new ArrayList<>();
        for (JsonNode lot : lots) {
            ids.add(id(lot).toString());
        }
        Reply reply = api.post(world.operatorA, "/api/transformations", map("type", type, "machineId", id(machine).toString(), "batchIds", ids));
        assertThat(reply.status()).as("preparar una transformación: %s", reply.raw()).isEqualTo(201);
        world.transformation = reply.body();
        return reply.body();
    }

    protected Reply completeTransformation(JsonNode transformation, Map<String, Object> body) {
        return api.post(world.operatorA, "/api/transformations/" + id(transformation) + "/complete", body);
    }

    /** Retiene el lote por calidad con el comando interno de Quality (el mismo HTTP real que usará Quality). */
    protected void holdForQuality(JsonNode lot) {
        Reply reply = api.internal("/internal/batches/" + id(lot) + "/phase-transitions",
                map("operation", "HOLD", "requestId", "falla-" + UUID.randomUUID(), "reason", "Posible falla derivada"));
        assertThat(reply.status()).as("retener el lote por calidad: %s", reply.raw()).isEqualTo(200);
        assertThat(reply.body().path("qualityHold").asBoolean()).isTrue();
    }

    /** Un producto final ya creado: transformación de un lote y finalización con una salida marcada como producto final. */
    protected JsonNode finalProduct() {
        JsonNode lot = registerLot(100);
        JsonNode transformation = startTransformation("DYEING", registerMachine(), lot);
        Reply completed = completeTransformation(transformation, map("outputs", List.of(
                map("name", "Tela teñida", "materialType", "Tela", "quantityKg", 90, "finalProduct", true)), "wasteKg", 5, "wasteReason", "Orillo"));
        assertThat(completed.status()).as("preparar un producto final: %s", completed.raw()).isEqualTo(200);
        JsonNode output = completed.body().path("outputs").get(0);
        world.batch = output;
        return output;
    }

    // ---- leer ----

    protected static UUID id(JsonNode node) {
        return UUID.fromString(node.path("id").asString());
    }

    protected JsonNode lot(Who who, JsonNode lot) {
        Reply reply = api.get(who, "/api/batches/" + id(lot));
        assertThat(reply.status()).as("consultar el lote %s: %s", id(lot), reply.raw()).isEqualTo(200);
        return reply.body();
    }

    protected Reply lotReply(Who who, JsonNode lot) {
        return api.get(who, "/api/batches/" + id(lot));
    }

    protected String phaseOf(JsonNode lot) {
        return lot(world.operatorA, lot).path("phase").asString();
    }

    protected long count(String sql, Object... params) {
        var statement = jdbc.sql(sql);
        for (Object param : params) {
            statement = statement.param(param);
        }
        return statement.query(Long.class).single();
    }

    protected void assertWithinTheRequest(String instant, String what) {
        assertThat(instant).as("%s debe estar registrada", what).isNotBlank();
        Instant value = Instant.parse(instant);
        // el servidor y la prueba comparten reloj: la fecha cae dentro de la petición (con un segundo de holgura)
        assertThat(value).as(what).isBetween(world.sentAt.minusSeconds(1), world.answeredAt.plusSeconds(1));
    }

    protected static void assertCatalogKeys(JsonNode payload, String... keys) {
        List<String> found = new ArrayList<>();
        payload.propertyNames().forEach(found::add);
        assertThat(found).as("el payload debe traer exactamente los campos del catálogo").containsExactlyInAnyOrder(keys);
    }
}
