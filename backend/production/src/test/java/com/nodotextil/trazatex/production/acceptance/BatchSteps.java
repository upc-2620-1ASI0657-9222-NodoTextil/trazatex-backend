package com.nodotextil.trazatex.production.acceptance;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US12 (registrar), US13 (buscar), US14 (consultar por QR) y US15 (dividir). */
public class BatchSteps extends StepSupport {

    private static final String TRACEABILITY_ID = "^TT-[0-9A-HJKMNP-TV-Z]{10}$";
    private static final String QR_CODE = "^[0-9A-HJKMNP-TV-Z]{26}$";

    public BatchSteps(World world, ProductionApi api, Outbox outbox, JdbcClient jdbc, ObjectMapper json) {
        super(world, api, outbox, jdbc, json);
    }

    private void prepareRegistration(Map<String, Object> body) {
        world.body = body;
        world.pending = () -> api.post(world.operatorA, "/api/batches", world.body);
    }

    // ---- US12 esc. 1 ----

    @Dado("^que el operario completa la información obligatoria$")
    public void theOperatorFillsInTheRequiredInformation() {
        Map<String, Object> body = lotBody(100);
        body.remove("composition");
        prepareRegistration(body);
    }

    @Dado("^registra una composición cuyos porcentajes suman 100 %$")
    public void registersACompositionAddingUpTo100() {
        world.body.put("composition", List.of(component("Algodón", 70), component("Poliéster", 30)));
    }

    @Entonces("^el sistema crea el lote como Disponible y No revisado$")
    public void theSystemCreatesTheBatchAsAvailableAndNotReviewed() {
        assertThat(world.last.status()).as("registrar el lote: %s", world.last.raw()).isEqualTo(201);
        assertThat(world.last.text("phase")).isEqualTo("AVAILABLE");
        assertThat(world.last.text("qualityStatus")).isEqualTo("NOT_REVIEWED");
        assertThat(world.last.field("qualityHold").asBoolean()).isFalse();
        assertThat(world.last.text("companyId")).isEqualTo(World.COMPANY_A.toString());
        JsonNode stored = lot(world.operatorA, world.last.body());
        assertThat(stored.path("phase").asString()).isEqualTo("AVAILABLE");
        assertThat(stored.path("quantityKg").decimalValue()).isEqualByComparingTo("100");
        assertThat(stored.path("composition")).hasSize(2);
    }

    @Y("^genera un Traceability ID y QR únicos\\.$")
    public void itGeneratesUniqueTraceabilityIdAndQr() {
        JsonNode first = world.last.body();
        assertThat(first.path("traceabilityId").asString()).matches(TRACEABILITY_ID);
        assertThat(first.path("qrCode").asString()).matches(QR_CODE);
        assertThat(first.path("qrCode").asString()).as("el QR no es el Traceability ID").isNotEqualTo(first.path("traceabilityId").asString());
        // único: otro lote con los mismos datos recibe otros códigos
        JsonNode second = registerLot(lotBody(100));
        assertThat(second.path("traceabilityId").asString()).isNotEqualTo(first.path("traceabilityId").asString());
        assertThat(second.path("qrCode").asString()).isNotEqualTo(first.path("qrCode").asString());
        // y es lo que se publica para los demás servicios
        List<Outbox.Event> registered = outbox.ofType("BatchRegistered", first.path("id").asString());
        assertThat(registered).hasSize(1);
        assertCatalogKeys(registered.get(0).payload(), "batchId", "traceabilityId", "qrCode", "companyId", "name", "materialType", "composition",
                "quantityKg", "origin", "supplierName", "receptionCharacteristics", "createdAt");
        // name (agregado en V2): el que envió el operario y el que guarda el lote
        assertThat(registered.get(0).payload().path("name").asString()).isEqualTo(world.body.get("name")).isEqualTo(first.path("name").asString());
        assertThat(registered.get(0).payload().path("traceabilityId").asString()).isEqualTo(first.path("traceabilityId").asString());
        assertThat(registered.get(0).payload().path("qrCode").asString()).isEqualTo(first.path("qrCode").asString());
        assertThat(registered.get(0).topic()).isEqualTo("production.batch");
    }

    // ---- US12 esc. 2 ----

    @Dado("^que el operario no conoce el nombre del proveedor$")
    public void theOperatorDoesNotKnowTheSupplier() {
        Map<String, Object> body = lotBody(100);
        body.remove("supplierName");
        prepareRegistration(body);
    }

    @Entonces("^el sistema permite continuar con el registro\\.$")
    public void theSystemLetsTheRegistrationContinue() {
        assertThat(world.last.status()).as("registrar sin proveedor: %s", world.last.raw()).isEqualTo(201);
        assertThat(world.last.field("supplierName") == null || world.last.field("supplierName").isNull()).isTrue();
        assertThat(lot(world.operatorA, world.last.body()).path("phase").asString()).isEqualTo("AVAILABLE");
    }

    // ---- US12 esc. 3 ----

    @Dado("^que los porcentajes no suman 100 %$")
    public void thePercentagesDoNotAddUpTo100() {
        Map<String, Object> body = lotBody(100);
        body.put("composition", List.of(component("Algodón", 60), component("Poliéster", 30)));
        prepareRegistration(body);
    }

    @Entonces("^el sistema no realiza el registro$")
    public void theSystemDoesNotRegister() {
        assertThat(world.last.status()).as("registro rechazado: %s", world.last.raw()).isEqualTo(400);
        assertThat(count("SELECT count(*) FROM batches")).as("no se creó ningún lote").isZero();
        assertThat(api.get(world.operatorA, "/api/batches").body().path("items")).isEmpty();
        assertThat(outbox.count()).as("no se publicó ningún evento").isZero();
    }

    @Y("^comunica que la composición no es válida\\.$")
    public void itSaysTheCompositionIsNotValid() {
        assertThat(world.last.code()).isEqualTo("INVALID_COMPOSITION");
        assertThat(world.last.message()).contains("composición no es válida");
    }

    // ---- US13 ----

    private record Search(String label, String query, Set<UUID> expected) {
    }

    @Dado("^que existen lotes registrados$")
    public void batchesExist() {
        Map<String, Object> fiber = lotBody(100);
        Map<String, Object> yarn = lotBody(80);
        yarn.put("materialType", "Hilo");
        Map<String, Object> fabric = lotBody(60);
        fabric.put("materialType", "Tela");
        JsonNode a = registerLot(fiber);
        JsonNode b = registerLot(yarn);
        JsonNode c = registerLot(fabric);
        // y uno de otra empresa, que nunca debe aparecer
        Reply foreign = api.post(world.operatorB, "/api/batches", lotBody(10));
        assertThat(foreign.status()).isEqualTo(201);
        world.notes.put("foreign", foreign.body());
        world.notes.put("searches", List.of(
                new Search("por tipo de material", "materialType=Hilo", Set.of(id(b))),
                new Search("por Traceability ID", "traceabilityId=" + a.path("traceabilityId").asString().substring(0, 8).toLowerCase(), Set.of(id(a))),
                new Search("por fase y calidad", "phase=AVAILABLE&qualityStatus=NOT_REVIEWED", Set.of(id(a), id(b), id(c))),
                new Search("por fecha de registro (hoy en Lima)", "createdFrom=" + LocalDate.now(ZoneId.of("America/Lima")) + "&createdTo=" + LocalDate.now(ZoneId.of("America/Lima")),
                        Set.of(id(a), id(b), id(c))),
                new Search("sin coincidencias", "materialType=Seda", Set.of())));
    }

    @Cuando("^el usuario realiza una búsqueda o aplica filtros$")
    public void theUserSearchesOrFilters() {
        world.replies.clear();
        @SuppressWarnings("unchecked")
        List<Search> searches = (List<Search>) world.notes.get("searches");
        for (Search search : searches) {
            world.send(() -> api.get(world.adminA, "/api/batches?" + search.query()));
        }
    }

    @Entonces("^el sistema muestra los lotes coincidentes$")
    @SuppressWarnings("unchecked")
    public void theSystemShowsTheMatchingBatches() {
        List<Search> searches = (List<Search>) world.notes.get("searches");
        JsonNode foreign = (JsonNode) world.notes.get("foreign");
        for (int i = 0; i < searches.size(); i++) {
            Reply reply = world.replies.get(i);
            assertThat(reply.status()).as("%s: %s", searches.get(i).label(), reply.raw()).isEqualTo(200);
            Set<UUID> found = new HashSet<>();
            reply.body().path("items").forEach(item -> found.add(id(item)));
            assertThat(found).as("%s", searches.get(i).label()).isEqualTo(searches.get(i).expected());
            assertThat(found).as("el lote de otra empresa nunca aparece").doesNotContain(id(foreign));
        }
    }

    @Y("^permite acceder a la información autorizada de cada lote\\.$")
    @SuppressWarnings("unchecked")
    public void itAllowsAccessToTheAuthorizedInformationOfEachBatch() {
        JsonNode foreign = (JsonNode) world.notes.get("foreign");
        Set<UUID> shown = new HashSet<>();
        world.replies.forEach(reply -> reply.body().path("items").forEach(item -> shown.add(id(item))));
        assertThat(shown).hasSize(3);
        for (UUID batch : shown) {
            Reply own = api.get(world.operatorA, "/api/batches/" + batch);
            assertThat(own.status()).isEqualTo(200);
            assertThat(own.body().path("composition")).isNotEmpty();
            assertThat(own.text("origin")).isEqualTo("Piura");
            assertThat(api.get(world.operatorB, "/api/batches/" + batch).status()).as("otra empresa no accede al detalle").isEqualTo(404);
        }
        assertThat(api.get(world.operatorA, "/api/batches/" + id(foreign)).status()).isEqualTo(404);
        assertThat(api.get(new Who(UUID.randomUUID(), "LICENSE_OWNER", null), "/api/batches").status()).as("el Dueño de Licencia no opera lotes").isEqualTo(403);
    }

    // ---- US14 ----

    @Dado("^que existe un lote registrado con un código QR asociado$")
    public void aBatchWithAQrExists() {
        world.batch = registerLot(40);
        assertThat(world.batch.path("qrCode").asString()).matches(QR_CODE);
        world.pending = () -> api.get(world.operatorA, "/api/batches/qr/" + world.batch.path("qrCode").asString());
    }

    @Cuando("^el usuario escanea el código$")
    public void theUserScansTheCode() {
        world.submit();
    }

    @Entonces("^el sistema identifica el lote$")
    public void theSystemIdentifiesTheBatch() {
        assertThat(world.last.status()).as("escanear: %s", world.last.raw()).isEqualTo(200);
        assertThat(world.last.text("access")).isEqualTo("OWN");
        assertThat(world.last.field("batch").path("id").asString()).isEqualTo(world.batch.path("id").asString());
        assertThat(world.last.field("batch").path("traceabilityId").asString()).isEqualTo(world.batch.path("traceabilityId").asString());
    }

    @Y("^muestra la información autorizada\\.$")
    public void itShowsTheAuthorizedInformation() {
        // la empresa dueña ve todo
        JsonNode own = world.last.field("batch");
        assertThat(own.path("composition")).hasSize(2);
        assertThat(own.path("origin").asString()).isEqualTo("Piura");
        assertThat(own.path("supplierName").asString()).isEqualTo("Textiles del Norte");
        assertThat(own.path("createdBy").asString()).isEqualTo(world.operatorA.userId().toString());
        // otra empresa ve solo lo siempre compartido: sin operario, proveedor, composición ni datos comerciales
        Reply other = api.get(world.operatorB, "/api/batches/qr/" + world.batch.path("qrCode").asString());
        assertThat(other.status()).isEqualTo(200);
        assertThat(other.text("access")).isEqualTo("SHARED");
        List<String> fields = new ArrayList<>();
        other.field("batch").propertyNames().forEach(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("batchId", "traceabilityId", "qrCode", "companyId", "materialType", "phase", "qualityStatus",
                "createdAt", "finalProduct");
        assertThat(other.field("batch").path("companyId").asString()).isEqualTo(World.COMPANY_A.toString());
    }

    @Dado("^que el código no corresponde a un lote registrado$")
    public void theCodeDoesNotMatchAnyBatch() {
        registerLot(10);
        String unknown = "0123456789ABCDEFGHJKMNPQRS";
        world.pending = () -> api.get(world.operatorA, "/api/batches/qr/" + unknown);
    }

    @Cuando("^el usuario realiza el escaneo$")
    public void theUserPerformsTheScan() {
        world.submit();
    }

    @Entonces("^el sistema no muestra información$")
    public void theSystemShowsNoInformation() {
        assertThat(world.last.status()).isEqualTo(404);
        assertThat(world.last.field("batch")).isNull();
        assertThat(world.last.raw()).doesNotContain("traceabilityId").doesNotContain("composition");
        // un código mal formado se trata igual: no revela nada
        Reply malformed = api.get(world.operatorA, "/api/batches/qr/no-es-un-codigo");
        assertThat(malformed.status()).isEqualTo(404);
        assertThat(malformed.code()).isEqualTo("QR_NOT_FOUND");
    }

    @Y("^comunica que el código no fue encontrado\\.$")
    public void itSaysTheCodeWasNotFound() {
        assertThat(world.last.code()).isEqualTo("QR_NOT_FOUND");
        assertThat(world.last.message()).contains("Código no encontrado");
    }

    // ---- US15 ----

    @Dado("^que existe un lote habilitado$")
    public void anEnabledBatchExists() {
        world.batch = registerLot(10);
    }

    @Dado("^el operario registra dos o más lotes hijos cuya cantidad total equivale a la cantidad del lote padre$")
    public void theOperatorRegistersChildrenAddingUpToTheParent() {
        world.body = map("children", List.of(map("name", "Hijo 1", "quantityKg", 4), map("name", "Hijo 2", "quantityKg", 6)));
        world.pending = () -> api.post(world.operatorA, "/api/batches/" + id(world.batch) + "/split", world.body);
    }

    @Entonces("^el lote padre cambia a Dividido$")
    public void theParentBecomesSplit() {
        assertThat(world.last.status()).as("dividir: %s", world.last.raw()).isEqualTo(201);
        assertThat(lot(world.operatorA, world.batch).path("phase").asString()).isEqualTo("SPLIT");
        List<Outbox.Event> changes = outbox.ofType("BatchPhaseChanged", id(world.batch).toString());
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).payload().path("previousPhase").asString()).isEqualTo("AVAILABLE");
        assertThat(changes.get(0).payload().path("newPhase").asString()).isEqualTo("SPLIT");
    }

    @Y("^los hijos nacen Disponibles con nuevos Traceability ID y QR\\.$")
    public void theChildrenAreBornAvailableWithNewCodes() {
        JsonNode children = world.last.field("children");
        assertThat(children).hasSize(2);
        Set<String> traceabilityIds = new HashSet<>(Set.of(world.batch.path("traceabilityId").asString()));
        Set<String> qrCodes = new HashSet<>(Set.of(world.batch.path("qrCode").asString()));
        for (JsonNode child : children) {
            JsonNode stored = lot(world.operatorA, child);
            assertThat(stored.path("phase").asString()).isEqualTo("AVAILABLE");
            assertThat(stored.path("traceabilityId").asString()).matches(TRACEABILITY_ID);
            assertThat(traceabilityIds.add(stored.path("traceabilityId").asString())).as("Traceability ID nuevo").isTrue();
            assertThat(qrCodes.add(stored.path("qrCode").asString())).as("QR nuevo").isTrue();
            assertThat(stored.path("originType").asString()).isEqualTo("SPLIT");
        }
        // y los demás servicios los conocen por BatchSplit
        List<Outbox.Event> split = outbox.ofType("BatchSplit", id(world.batch).toString());
        assertThat(split).hasSize(1);
        assertCatalogKeys(split.get(0).payload(), "parentBatchId", "companyId", "children");
        assertThat(split.get(0).payload().path("children")).hasSize(2);
        for (int i = 0; i < 2; i++) {
            JsonNode published = split.get(0).payload().path("children").get(i);
            assertCatalogKeys(published, "batchId", "traceabilityId", "qrCode", "name", "materialType", "quantityKg", "composition", "origin",
                    "supplierName", "receptionCharacteristics", "qualityStatus", "createdAt");
            // name (agregado en V2): el que el operario dio a ese hijo y el que guarda el hijo
            @SuppressWarnings("unchecked")
            Object requestedName = ((List<Map<String, Object>>) world.body.get("children")).get(i).get("name");
            assertThat(published.path("name").asString()).isEqualTo(requestedName).isEqualTo(lot(world.operatorA, children.get(i)).path("name").asString());
            assertThat(published.path("materialType").asString()).isEqualTo(world.batch.path("materialType").asString());
            assertThat(published.path("origin").asString()).isEqualTo(world.batch.path("origin").asString());
            assertThat(published.path("supplierName").asString()).isEqualTo(world.batch.path("supplierName").asString());
            assertThat(published.path("batchId").asString()).isEqualTo(children.get(i).path("id").asString());
            assertThat(published.path("traceabilityId").asString()).isEqualTo(children.get(i).path("traceabilityId").asString());
        }
    }

    @Dado("^que la suma de los hijos no equivale a la cantidad del padre$")
    public void theChildrenDoNotAddUpToTheParent() {
        world.batch = registerLot(10);
        world.body = map("children", List.of(map("name", "Hijo 1", "quantityKg", 4), map("name", "Hijo 2", "quantityKg", 5)));
        world.pending = () -> api.post(world.operatorA, "/api/batches/" + id(world.batch) + "/split", world.body);
    }

    @Entonces("^el sistema no realiza la operación$")
    public void theSystemDoesNotPerformTheOperation() {
        assertThat(world.last.status()).as("división rechazada: %s", world.last.raw()).isEqualTo(400);
        assertThat(lot(world.operatorA, world.batch).path("phase").asString()).as("el padre sigue disponible").isEqualTo("AVAILABLE");
        assertThat(count("SELECT count(*) FROM batches")).as("no nacieron hijos").isEqualTo(1);
        assertThat(outbox.ofType("BatchSplit")).isEmpty();
        assertThat(outbox.ofType("BatchPhaseChanged")).isEmpty();
    }

    @Y("^comunica que las cantidades no coinciden\\.$")
    public void itSaysTheQuantitiesDoNotMatch() {
        assertThat(world.last.code()).isEqualTo("SPLIT_QUANTITIES_MISMATCH");
        assertThat(world.last.message()).contains("cantidades no coinciden");
    }
}
