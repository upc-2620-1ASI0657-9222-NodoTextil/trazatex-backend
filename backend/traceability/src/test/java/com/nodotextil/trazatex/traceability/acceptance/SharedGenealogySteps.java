package com.nodotextil.trazatex.traceability.acceptance;

import com.nodotextil.trazatex.traceability.integration.EventMessages;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US36 — Consultar genealogía compartida (RF-018, RNF-07). Las empresas B y C configuran su privacidad con {@code CompanyPrivacyUpdated} (como lo publica
 * Organization &amp; Access), sus lotes y transformaciones llegan por los eventos de Production y un usuario de la empresa A abre la genealogía por la API. Lo que
 * se comprueba es el JSON que recibe: los campos Siempre Compartidos de todos los nodos, los Configurables solo donde esa empresa los compartió, y lo privado
 * ausente (no como {@code null}) y ni su valor en ningún lado de la respuesta.
 */
public class SharedGenealogySteps {

    private static final String ROOT = "/api/traceability";
    private static final List<String> BATCH_CONFIGURABLE = List.of("supplierName", "origin", "quantityKg", "composition", "receptionCharacteristics");
    private static final List<String> TRANSFORMATION_CONFIGURABLE = List.of("machineName", "machineType", "shrinkageKg", "wasteKg", "wasteReason");
    private static final List<String> BATCH_ALWAYS_SHARED = List.of("traceabilityId", "qrCode", "companyId", "businessName", "materialType", "phase", "qualityStatus", "createdAt",
            "finalProduct");
    private static final List<String> TRANSFORMATION_ALWAYS_SHARED = List.of("type", "startedAt", "completedAt", "companyId", "businessName");

    private final World world;
    private final Platform platform;
    private final Api api;

    public SharedGenealogySteps(World world, Platform platform, Api api) {
        this.world = world;
        this.platform = platform;
        this.api = api;
    }

    private UUID id(String name) {
        return world.ids.computeIfAbsent(name, key -> UUID.randomUUID());
    }

    private static Map<String, String> sharing(String... shared) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String field : List.of("SUPPLIER", "ORIGIN", "QUANTITY", "COMPOSITION", "RECEPTION_CHARACTERISTICS", "MACHINERY", "SHRINKAGE", "WASTE", "WASTE_REASON",
                "FAILURE_CAUSE", "QUALITY_TEST_VALUES")) {
            map.put(field, "PRIVATE");
        }
        for (String field : shared) {
            map.put(field, "SHARED");
        }
        return map;
    }

    private void company(UUID company, String name, Map<String, String> privacy) {
        platform.publishAndAwait(List.of(EventMessages.companyRegistered(UUID.randomUUID(), company, name), EventMessages.companyPrivacyUpdated(UUID.randomUUID(), company, privacy)));
    }

    private void register(String name, UUID company, String supplier, String origin) {
        platform.publishAndAwait(List.of(EventMessages.batchRegistered(UUID.randomUUID(), id(name), "TT-" + name, "QR-" + name, company, "Algodón", 100, origin, supplier,
                "Fibra limpia", World.T0)));
    }

    private void transform(String transformation, UUID company, String machine, List<String> inputs, String output) {
        platform.publishAndAwait(List.of(EventMessages.transformationCompleted(UUID.randomUUID(), id(transformation), "DYEING", company, machine, "Teñido",
                inputs.stream().map(this::id).toList(), id(output), "TT-" + output, "QR-" + output, "Tela teñida", 80, false, 5, "Orillo", 5, World.T0, World.T0.plusSeconds(3600))));
    }

    private void hold(String batch, UUID company) {
        platform.publishAndAwait(List.of(EventMessages.phaseChanged(UUID.randomUUID(), id(batch), company, "IN_TRANSFER", "AVAILABLE", true, 2)));
    }

    // ---- lo que dice la respuesta ----

    private JsonNode body() {
        assertThat(world.reply.status()).as("la API respondió %s: %s", world.reply.status(), world.reply.body()).isEqualTo(200);
        return world.reply.body();
    }

    private JsonNode node(String name) {
        for (JsonNode node : body().get("nodes")) {
            if (node.get("id").asString().equals(id(name).toString())) {
                return node;
            }
        }
        throw new AssertionError("«" + name + "» no está en la respuesta: " + body().get("nodes"));
    }

    private void assertShown(String name, List<String> fields) {
        for (String field : fields) {
            assertThat(node(name).has(field)).as("%s: %s se muestra", name, field).isTrue();
        }
    }

    private void assertHidden(String name, List<String> fields) {
        for (String field : fields) {
            assertThat(node(name).has(field)).as("%s: %s no sale en el JSON (ni como null)", name, field).isFalse();
        }
    }

    private static List<String> except(List<String> all, String... shown) {
        List<String> hidden = new java.util.ArrayList<>(all);
        hidden.removeAll(List.of(shown));
        return hidden;
    }

    // ==== Escenario 1: información autorizada ====

    @Dado("^que el usuario consulta una genealogía con información de varias empresas$")
    public void theUserQueriesAGenealogyWithInformationOfSeveralCompanies() {
        // La empresa B comparte proveedor, cantidad y maquinaria; la C solo el origen; la A (la que consulta) mezcla lo de las dos en un lote suyo
        company(world.companyB, "Bahía SAC", sharing("SUPPLIER", "QUANTITY", "MACHINERY"));
        UUID companyC = UUID.randomUUID();
        world.ids.put("companyC", companyC);
        company(companyC, "Cumbre SAC", sharing("ORIGIN"));
        company(world.companyA, "Andina SAC", sharing());
        register("B0", world.companyB, "Proveedor de B", "Origen de B");
        transform("TB", world.companyB, "Cardadora de B", List.of("B0"), "B1");
        register("C1", companyC, "Proveedor de C", "Origen de C");
        transform("TA", world.companyA, "Teñidora de A", List.of("B1", "C1"), "AO");
        world.batch = id("AO");
    }

    @Cuando("^visualiza sus lotes y relaciones$")
    public void theUserViewsTheBatchesAndRelationships() {
        world.reply = api.get(ROOT + "/batches/" + world.batch + "/lineage", world.companyA);
    }

    @Entonces("^el sistema muestra los campos Siempre Compartidos$")
    public void theAlwaysSharedFieldsAreShown() {
        assertThat(body().get("nodes")).as("A, B y C: 4 lotes y 2 transformaciones").hasSize(6);
        assertThat(body().get("edges")).as("las relaciones de los tres: B0→TB→B1→TA←C1 y TA→AO").hasSize(5);
        for (String batch : List.of("B0", "B1", "C1", "AO")) {
            assertShown(batch, BATCH_ALWAYS_SHARED);
            assertThat(node(batch).get("traceabilityId").asString()).isEqualTo("TT-" + batch);
        }
        for (String transformation : List.of("TB", "TA")) {
            assertShown(transformation, TRANSFORMATION_ALWAYS_SHARED);
        }
        assertThat(node("B0").get("businessName").asString()).isEqualTo("Bahía SAC");
        assertThat(node("C1").get("businessName").asString()).isEqualTo("Cumbre SAC");
        assertThat(node("B0").get("access").asString()).isEqualTo("SHARED");
    }

    @Y("^los campos Configurables establecidos como Compartidos por cada empresa\\.$")
    public void theConfigurableFieldsEachCompanySharedAreShown() {
        // B compartió proveedor, cantidad y maquinaria: se ven; sus demás campos configurables, no
        assertShown("B0", List.of("supplierName", "quantityKg"));
        assertThat(node("B0").get("supplierName").asString()).isEqualTo("Proveedor de B");
        assertHidden("B0", except(BATCH_CONFIGURABLE, "supplierName", "quantityKg"));
        assertShown("B1", List.of("quantityKg"));
        assertShown("TB", List.of("machineName", "machineType"));
        assertThat(node("TB").get("machineName").asString()).isEqualTo("Cardadora de B");
        assertHidden("TB", except(TRANSFORMATION_CONFIGURABLE, "machineName", "machineType"));
        // C compartió solo el origen
        assertShown("C1", List.of("origin"));
        assertThat(node("C1").get("origin").asString()).isEqualTo("Origen de C");
        assertHidden("C1", except(BATCH_CONFIGURABLE, "origin"));
        // y lo de A, la que consulta, se ve completo aunque A no comparta nada
        assertShown("AO", List.of("quantityKg"));
        assertShown("TA", TRANSFORMATION_CONFIGURABLE);
        assertThat(node("TA").get("access").asString()).isEqualTo("OWN");
    }

    // ==== Escenario 2: información privada ====

    @Dado("^que existen campos Configurables Privados o información Siempre Privada$")
    public void thereArePrivateConfigurableFieldsOrAlwaysPrivateInformation() {
        // La empresa B comparte solo el origen: su proveedor, cantidad, composición, recepción y todo de su transformación son Configurables Privados; además su lote
        // tiene retención por calidad (la retención es información Siempre Privada: solo la ve su empresa)
        company(world.companyB, "Bahía SAC", sharing("ORIGIN"));
        company(world.companyA, "Andina SAC", sharing());
        register("BP", world.companyB, "Proveedor secreto de B", "Origen de B");
        hold("BP", world.companyB);
        transform("TP", world.companyB, "Cardadora secreta de B", List.of("BP"), "BQ");
        transform("TQ", world.companyA, "Teñidora de A", List.of("BQ"), "AQ");
        world.batch = id("AQ");
    }

    @Cuando("^un usuario de otra empresa visualiza la genealogía$")
    public void aUserOfAnotherCompanyViewsTheGenealogy() {
        world.reply = api.get(ROOT + "/batches/" + world.batch + "/lineage", world.companyA);
    }

    @Entonces("^el sistema mantiene dichos datos ocultos\\.$")
    public void theSystemKeepsThatDataHidden() {
        assertThat(node("BP").get("access").asString()).isEqualTo("SHARED");
        assertHidden("BP", except(BATCH_CONFIGURABLE, "origin"));
        assertShown("BP", List.of("origin"));
        assertHidden("BP", List.of("qualityHold"));
        assertHidden("BQ", List.of("qualityHold"));
        assertHidden("TP", TRANSFORMATION_CONFIGURABLE);
        assertThat(body().toString()).as("ningún valor privado de B aparece en ningún lado de la respuesta")
                .doesNotContain("Proveedor secreto de B", "Cardadora secreta de B", "Fibra limpia");
        assertThat(node("AQ").has("qualityHold")).as("la empresa que consulta ve lo suyo, retención incluida").isTrue();
    }
}
