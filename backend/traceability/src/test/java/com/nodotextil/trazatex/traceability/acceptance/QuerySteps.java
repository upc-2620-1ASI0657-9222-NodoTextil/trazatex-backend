package com.nodotextil.trazatex.traceability.acceptance;

import com.nodotextil.trazatex.traceability.integration.EventMessages;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US30, US31 y US32 — consultar genealogías (RF-054 a RF-056). El grafo se arma como en US29: los pasos hacen de Production y de Organization &amp; Access y
 * publican sus eventos en los tópicos de Kafka (con el sobre y el payload del catálogo) y esperan a que traceability los procese. La consulta es la de un
 * usuario de verdad: una petición a la API con las cabeceras de identidad que inyecta el Gateway. Lo que se comprueba es la respuesta JSON.
 */
public class QuerySteps {

    /** Lo que solo ve la empresa dueña del lote (o la que lo compartió): nunca debe estar en un nodo de otra empresa. */
    private static final List<String> PRIVATE_BATCH_FIELDS = List.of("supplierName", "origin", "quantityKg", "composition", "receptionCharacteristics", "qualityHold",
            "transferHistory");
    private static final List<String> PRIVATE_TRANSFORMATION_FIELDS = List.of("machineName", "machineType", "shrinkageKg", "wasteKg", "wasteReason");

    private final World world;
    private final Platform platform;
    private final Api api;

    public QuerySteps(World world, Platform platform, Api api) {
        this.world = world;
        this.platform = platform;
        this.api = api;
    }

    // ---- el grafo, por eventos ----

    private UUID id(String name) {
        return world.ids.computeIfAbsent(name, key -> UUID.randomUUID());
    }

    private String trace(String name) {
        return "TT-" + name;
    }

    private String qr(String name) {
        return "QR-" + name;
    }

    private void register(String name, UUID company, String supplier, String origin) {
        platform.publishAndAwait(List.of(EventMessages.batchRegistered(UUID.randomUUID(), id(name), trace(name), qr(name), company, "Algodón", 100, origin, supplier,
                "Fibra limpia", World.T0)));
    }

    private void split(String parent, UUID company, String supplier, String origin, String... children) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String child : children) {
            list.add(EventMessages.splitChild(id(child), trace(child), qr(child), "Algodón", 50, origin, supplier, "Fibra limpia", "NOT_REVIEWED", World.T0.plusSeconds(60)));
        }
        platform.publishAndAwait(List.of(EventMessages.batchSplit(UUID.randomUUID(), id(parent), company, list)));
    }

    private void transform(String transformation, UUID company, String machine, List<String> inputs, String output, String supplier) {
        platform.publishAndAwait(List.of(EventMessages.transformationCompleted(UUID.randomUUID(), id(transformation), "DYEING", company, machine, "Teñido",
                inputs.stream().map(this::id).toList(), id(output), trace(output), qr(output), "Tela teñida", 80, false, 5, "Orillo", 5, World.T0, World.T0.plusSeconds(3600))));
    }

    private void transfer(String transfer, String batch, UUID from, UUID to) {
        platform.publishAndAwait(List.of(EventMessages.transferAccepted(UUID.randomUUID(), id(transfer), List.of(id(batch)), from, to, World.T0.plusSeconds(120)),
                EventMessages.phaseChanged(UUID.randomUUID(), id(batch), to, "IN_TRANSFER", "AVAILABLE", false, 2)));
    }

    private static final String ROOT = "/api/traceability";

    // ---- lo que dice la respuesta ----

    private JsonNode body() {
        assertThat(world.reply.status()).as("la API respondió %s: %s", world.reply.status(), world.reply.body()).isEqualTo(200);
        return world.reply.body();
    }

    private List<JsonNode> nodes(String kind) {
        List<JsonNode> found = new ArrayList<>();
        for (JsonNode node : body().get("nodes")) {
            if (node.get("kind").asString().equals(kind)) {
                found.add(node);
            }
        }
        return found;
    }

    private Set<UUID> idsOf(List<JsonNode> nodes) {
        return nodes.stream().map(node -> UUID.fromString(node.get("id").asString())).collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<UUID> idsOf(String... names) {
        Set<UUID> ids = new TreeSet<>();
        for (String name : names) {
            ids.add(id(name));
        }
        return ids;
    }

    private JsonNode node(String name) {
        for (JsonNode node : body().get("nodes")) {
            if (node.get("id").asString().equals(id(name).toString())) {
                return node;
            }
        }
        throw new AssertionError("«" + name + "» no está en la respuesta: " + body().get("nodes"));
    }

    /** Las relaciones de la respuesta como {@code TIPO origen>destino} (por nombre), ordenadas. */
    private Set<String> edges() {
        Map<UUID, String> names = new TreeMap<>();
        world.ids.forEach((name, uuid) -> names.put(uuid, name));
        Set<String> edges = new TreeSet<>();
        for (JsonNode edge : body().get("edges")) {
            edges.add(edge.get("type").asString() + " " + names.get(UUID.fromString(edge.get("from").asString())) + ">"
                    + names.get(UUID.fromString(edge.get("to").asString())));
        }
        return edges;
    }

    private void assertLevels(Map<String, Integer> expected) {
        expected.forEach((name, level) -> assertThat(node(name).get("level").asInt()).as("nivel de %s", name).isEqualTo(level));
    }

    // ==== US30 — Buscar genealogías ====

    @Dado("^que existen genealogías registradas$")
    public void genealogiesAreRegistered() {
        // la de la empresa A: un lote dividido en dos; otro lote suyo sin relación con ese prefijo; y un lote de la empresa B que comparte el prefijo
        register("7A0001", world.companyA, "Textiles del Norte", "Piura");
        split("7A0001", world.companyA, "Textiles del Norte", "Piura", "7A0002", "7A0003");
        register("9B0001", world.companyA, "Textiles del Norte", "Piura");
        register("7A00F1", world.companyB, "Proveedor secreto de B", "Origen secreto de B");
    }

    @Cuando("^el usuario realiza una búsqueda$")
    public void theUserSearches() {
        // un operario de la empresa A teclea el prefijo a mano, en minúsculas
        world.reply = api.get(ROOT + "/lineages?traceabilityId=tt-7a00", world.companyA);
    }

    @Entonces("^el sistema muestra las genealogías coincidentes$")
    public void theMatchingGenealogiesAreShown() {
        JsonNode page = body();
        Set<String> found = new TreeSet<>();
        for (JsonNode item : page.get("items")) {
            found.add(item.get("traceabilityId").asString());
            assertThat(item.get("companyId").asString()).as("el prefijo solo lista los lotes de la propia empresa").isEqualTo(world.companyA.toString());
        }
        assertThat(found).as("los lotes de la empresa A que empiezan con el prefijo, y ninguno más (ni el de B con el mismo prefijo ni el que no coincide)")
                .containsExactly("TT-7A0001", "TT-7A0002", "TT-7A0003");
        assertThat(page.get("totalElements").asInt()).isEqualTo(3);
    }

    @Y("^permite acceder a aquellas que puede consultar\\.$")
    public void theOnesTheUserMayConsultCanBeOpened() {
        for (JsonNode item : body().get("items")) {
            Api.Reply opened = api.get(ROOT + "/batches/" + item.get("batchId").asString() + "/lineage", world.companyA);
            assertThat(opened.status()).as("abrir la genealogía de %s", item.get("traceabilityId").asString()).isEqualTo(200);
            JsonNode start = null;
            for (JsonNode node : opened.body().get("nodes")) {
                if (node.get("level").asInt() == 0) {
                    start = node;
                }
            }
            assertThat(start).isNotNull();
            assertThat(start.get("id").asString()).isEqualTo(item.get("batchId").asString());
            assertThat(start.get("access").asString()).isEqualTo("OWN");
        }
        // y también la de otra empresa, que se encuentra por su QR: se abre, pero solo muestra lo siempre compartido
        Api.Reply foreignSearch = api.get(ROOT + "/lineages?qrCode=" + qr("7A00F1"), world.companyA);
        assertThat(foreignSearch.body().get("items")).hasSize(1);
        assertThat(foreignSearch.body().get("items").get(0).get("access").asString()).isEqualTo("SHARED");
        Api.Reply foreignOpened = api.get(ROOT + "/batches/" + id("7A00F1") + "/lineage", world.companyA);
        assertThat(foreignOpened.status()).isEqualTo(200);
        JsonNode foreign = foreignOpened.body().get("nodes").get(0);
        assertThat(foreign.get("access").asString()).isEqualTo("SHARED");
        assertThat(foreign.has("supplierName")).isFalse();
        assertThat(foreignOpened.body().toString()).doesNotContain("Proveedor secreto de B", "Origen secreto de B");
    }

    // ==== US31 — Consultar trazabilidad hacia atrás ====

    @Dado("^que existe un lote con antecedentes registrados$")
    public void aBatchWithAntecedentsIsRegistered() {
        // La empresa B hila dos lotes en uno (B3), que transfiere a la empresa A; A lo mezcla con un lote suyo y obtiene Z
        register("B1", world.companyB, "Proveedor secreto de B", "Origen secreto de B");
        register("B2", world.companyB, "Proveedor secreto de B", "Origen secreto de B");
        transform("TB", world.companyB, "Cardadora secreta de B", List.of("B1", "B2"), "B3", "Proveedor secreto de B");
        transfer("TRF", "B3", world.companyB, world.companyA);
        register("A1", world.companyA, "Proveedor de A", "Piura");
        transform("TA", world.companyA, "Teñidora de A", List.of("B3", "A1"), "Z", "Proveedor de A");
        world.batch = id("Z");
    }

    @Cuando("^consulta su trazabilidad hacia atrás$")
    public void theUserQueriesTheTraceabilityBackwards() {
        world.reply = api.get(ROOT + "/batches/" + world.batch + "/ancestors", world.companyA);
    }

    @Entonces("^el sistema muestra los lotes y relaciones que dieron origen al lote$")
    public void theOriginBatchesAndRelationshipsAreShown() {
        assertThat(body().get("direction").asString()).isEqualTo("ANCESTORS");
        assertThat(idsOf(nodes("BATCH"))).as("el lote y todos sus antecesores, de las dos empresas, y ningún otro").isEqualTo(idsOf("Z", "B3", "A1", "B1", "B2"));
        assertThat(idsOf(nodes("TRANSFORMATION"))).as("las dos transformaciones que dieron origen al lote").isEqualTo(idsOf("TA", "TB"));
        assertThat(edges()).as("las relaciones entre ellos: entradas, transformaciones y salidas").containsExactlyInAnyOrder(
                "INPUT_OF A1>TA", "INPUT_OF B3>TA", "PRODUCED TA>Z", "INPUT_OF B1>TB", "INPUT_OF B2>TB", "PRODUCED TB>B3");
        assertLevels(Map.of("Z", 0, "B3", -1, "A1", -1, "B1", -2, "B2", -2));
        assertThat(body().get("complete").asBoolean()).isTrue();
        assertThat(body().get("truncated").asBoolean()).isFalse();
    }

    @Y("^respeta las reglas de privacidad de las empresas involucradas\\.$")
    public void thePrivacyRulesOfTheCompaniesInvolvedAreRespected() {
        // Z, A1 y TA son de A; B3 se transfirió a A y también es de A: los ve completos. B1, B2 y TB siguen siendo de B: solo lo siempre compartido
        for (String own : List.of("Z", "A1", "B3")) {
            JsonNode node = node(own);
            assertThat(node.get("access").asString()).as("%s es de la empresa que consulta", own).isEqualTo("OWN");
            assertThat(node.has("quantityKg")).as("%s: su dueña ve la cantidad", own).isTrue();
        }
        assertThat(node("A1").get("supplierName").asString()).isEqualTo("Proveedor de A");
        assertThat(node("A1").get("origin").asString()).isEqualTo("Piura");
        assertThat(node("TA").get("machineName").asString()).isEqualTo("Teñidora de A");
        for (String foreign : List.of("B1", "B2")) {
            JsonNode node = node(foreign);
            assertThat(node.get("access").asString()).as("%s es de otra empresa", foreign).isEqualTo("SHARED");
            assertThat(node.get("traceabilityId").asString()).as("lo siempre compartido sí se ve").isEqualTo(trace(foreign));
            assertThat(node.get("companyId").asString()).isEqualTo(world.companyB.toString());
            for (String field : PRIVATE_BATCH_FIELDS) {
                assertThat(node.has(field)).as("%s no sale en un lote de otra empresa (%s)", field, foreign).isFalse();
            }
        }
        JsonNode foreignTransformation = node("TB");
        assertThat(foreignTransformation.get("access").asString()).isEqualTo("SHARED");
        assertThat(foreignTransformation.has("type")).as("el tipo de la transformación es siempre compartido").isTrue();
        for (String field : PRIVATE_TRANSFORMATION_FIELDS) {
            assertThat(foreignTransformation.has(field)).as("%s no sale en una transformación de otra empresa", field).isFalse();
        }
        assertThat(body().toString()).as("ningún valor privado de la otra empresa aparece en ningún lado de la respuesta")
                .doesNotContain("Cardadora secreta de B", "Origen secreto de B", "Proveedor secreto de B");
        assertThat(world.reply.cacheControl()).as("la respuesta depende de quien consulta").isEqualTo("no-store");
    }

    // ==== US32 — Consultar trazabilidad hacia adelante ====

    @Dado("^que existe un lote con descendientes registrados$")
    public void aBatchWithDescendantsIsRegistered() {
        // R se divide en S1 y S2; S1 se mezcla con X (otro lote, que no deriva de R) y da U; S2 se divide en V1 y V2
        register("R", world.companyA, "Textiles del Norte", "Piura");
        register("X", world.companyA, "Textiles del Norte", "Piura");
        split("R", world.companyA, "Textiles del Norte", "Piura", "S1", "S2");
        transform("T1", world.companyA, "Teñidora de A", List.of("S1", "X"), "U", "Textiles del Norte");
        split("S2", world.companyA, "Textiles del Norte", "Piura", "V1", "V2");
        world.batch = id("R");
    }

    @Cuando("^consulta su trazabilidad hacia adelante$")
    public void theUserQueriesTheTraceabilityForwards() {
        world.reply = api.get(ROOT + "/batches/" + world.batch + "/descendants", world.companyA);
    }

    @Entonces("^el sistema muestra los lotes derivados y sus relaciones$")
    public void theDerivedBatchesAndRelationshipsAreShown() {
        assertThat(body().get("direction").asString()).isEqualTo("DESCENDANTS");
        assertThat(idsOf(nodes("BATCH"))).as("el lote y sus derivados; X no deriva de él y no está").isEqualTo(idsOf("R", "S1", "S2", "U", "V1", "V2"));
        assertThat(idsOf(nodes("TRANSFORMATION"))).isEqualTo(idsOf("T1"));
        assertThat(edges()).containsExactlyInAnyOrder("SPLIT_INTO R>S1", "SPLIT_INTO R>S2", "INPUT_OF S1>T1", "PRODUCED T1>U", "SPLIT_INTO S2>V1", "SPLIT_INTO S2>V2");
        assertLevels(Map.of("R", 0, "S1", 1, "S2", 1, "U", 2, "V1", 2, "V2", 2));
        assertThat(body().get("complete").asBoolean()).isTrue();
        assertThat(body().get("truncated").asBoolean()).isFalse();
    }

    @Y("^representa las ramificaciones existentes\\.$")
    public void theExistingBranchesAreRepresented() {
        Map<String, List<String>> childrenOf = new TreeMap<>();
        for (String edge : edges()) {
            String[] parts = edge.split(" ")[1].split(">");
            childrenOf.computeIfAbsent(parts[0], key -> new ArrayList<>()).add(parts[1]);
        }
        assertThat(childrenOf.get("R")).as("R se ramifica en dos").containsExactlyInAnyOrder("S1", "S2");
        assertThat(childrenOf.get("S2")).as("S2 se ramifica de nuevo en dos").containsExactlyInAnyOrder("V1", "V2");
        assertThat(childrenOf.get("S1")).as("S1 sigue por una transformación").containsExactly("T1");
        assertThat(nodes("BATCH").stream().filter(node -> node.get("level").asInt() == 1).count()).as("dos ramas en el primer nivel").isEqualTo(2);
        Set<String> reached = new TreeSet<>(childrenOf.values().stream().flatMap(List::stream).toList());
        assertThat(reached).as("toda rama llega a un nodo de la respuesta: nada queda suelto").containsExactlyInAnyOrder("S1", "S2", "T1", "U", "V1", "V2");
    }
}
