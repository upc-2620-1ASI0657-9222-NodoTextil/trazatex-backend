package com.nodotextil.trazatex.production.acceptance;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** US10 (maquinaria) y los escenarios de US16 que hablan de una maquinaria que no se puede usar. */
public class MachineSteps extends StepSupport {

    public MachineSteps(World world, ProductionApi api, Outbox outbox, JdbcClient jdbc, ObjectMapper json) {
        super(world, api, outbox, jdbc, json);
    }

    // ---- US10 esc. 1 ----

    @Dado("^que el administrador completa código, nombre y tipo$")
    public void theAdministratorFillsInCodeNameAndType() {
        world.body = map("internalCode", "MQ-01", "name", "Cardadora de fibras", "type", "Cardado");
        world.pending = () -> api.post(world.adminA, "/api/machines", world.body);
    }

    @Entonces("^el sistema registra la maquinaria como Activa\\.$")
    public void theSystemRegistersTheMachineAsActive() {
        assertThat(world.last.status()).as("registrar la maquinaria: %s", world.last.raw()).isEqualTo(201);
        assertThat(world.last.text("status")).isEqualTo("ACTIVE");
        assertThat(world.last.text("internalCode")).isEqualTo("MQ-01");
        assertThat(world.last.text("companyId")).isEqualTo(World.COMPANY_A.toString());
        Reply detail = api.get(world.adminA, "/api/machines/" + world.last.text("id"));
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body().path("machine").path("status").asString()).isEqualTo("ACTIVE");
        assertThat(detail.body().path("history")).hasSize(1);
        assertThat(detail.body().path("history").get(0).path("from").isNull()).as("el registro es la primera fila del historial").isTrue();
        assertThat(detail.body().path("history").get(0).path("to").asString()).isEqualTo("ACTIVE");
    }

    // ---- US10 esc. 2 ----

    @Dado("^que existe una maquinaria registrada$")
    public void aMachineExists() {
        world.machine = registerMachine();
        assertThat(world.machine.path("status").asString()).isEqualTo("ACTIVE");
    }

    @Cuando("^cambia su estado$")
    public void itsStatusChanges() {
        world.send(() -> api.patch(world.adminA, "/api/machines/" + id(world.machine) + "/status", map("status", "OUT_OF_SERVICE")));
    }

    @Entonces("^el sistema permite establecerla como Activa, Fuera de servicio o Inactiva$")
    public void theSystemAllowsActiveOutOfServiceOrInactive() {
        assertThat(world.last.status()).as("pasar a Fuera de servicio: %s", world.last.raw()).isEqualTo(200);
        assertThat(world.last.text("status")).isEqualTo("OUT_OF_SERVICE");
        Reply inactive = api.patch(world.adminA, "/api/machines/" + id(world.machine) + "/status", map("status", "INACTIVE"));
        assertThat(inactive.status()).as("pasar a Inactiva: %s", inactive.raw()).isEqualTo(200);
        assertThat(inactive.text("status")).isEqualTo("INACTIVE");
        Reply active = api.patch(world.adminA, "/api/machines/" + id(world.machine) + "/status", map("status", "ACTIVE"));
        assertThat(active.status()).as("volver a Activa: %s", active.raw()).isEqualTo(200);
        assertThat(active.text("status")).isEqualTo("ACTIVE");
    }

    @Y("^conserva su historial\\.$")
    public void itKeepsItsHistory() {
        Reply detail = api.get(world.adminA, "/api/machines/" + id(world.machine));
        JsonNode history = detail.body().path("history");
        List<String> steps = new ArrayList<>();
        history.forEach(change -> steps.add((change.path("from").isNull() ? "-" : change.path("from").asString()) + ">" + change.path("to").asString()));
        assertThat(steps).containsExactly("->ACTIVE", "ACTIVE>OUT_OF_SERVICE", "OUT_OF_SERVICE>INACTIVE", "INACTIVE>ACTIVE");
        history.forEach(change -> assertThat(change.path("changedBy").asString()).isEqualTo(world.adminA.userId().toString()));
        for (int i = 1; i < history.size(); i++) {
            assertThat(java.time.Instant.parse(history.get(i).path("changedAt").asString()))
                    .isAfterOrEqualTo(java.time.Instant.parse(history.get(i - 1).path("changedAt").asString()));
        }
    }

    // ---- US10 esc. 3 y US16 esc. 3: una maquinaria que no está activa no se puede usar ----

    private void anOutOfServiceAndAnInactiveMachineExist() {
        JsonNode outOfService = registerMachine();
        setMachineStatus(outOfService, "OUT_OF_SERVICE");
        JsonNode inactive = registerMachine();
        setMachineStatus(inactive, "INACTIVE");
        world.batch = registerLot(50);
    }

    private void anOperatorTriesToUseThem() {
        world.replies.clear();
        for (JsonNode machine : world.machines) {
            world.send(() -> api.post(world.operatorA, "/api/transformations",
                    map("type", "SPINNING", "machineId", id(machine).toString(), "batchIds", List.of(id(world.batch).toString()))));
        }
    }

    private void theTransformationIsRefused() {
        assertThat(world.replies).hasSize(2);
        for (Reply reply : world.replies) {
            assertThat(reply.status()).as("usar una maquinaria no activa: %s", reply.raw()).isEqualTo(409);
            assertThat(reply.code()).isEqualTo("MACHINE_NOT_ACTIVE");
        }
        Reply transformations = api.get(world.operatorA, "/api/transformations");
        assertThat(transformations.body().path("items")).as("no se creó ninguna transformación").isEmpty();
        assertThat(phaseOf(world.batch)).as("el lote sigue disponible").isEqualTo("AVAILABLE");
        assertThat(outbox.ofType("BatchPhaseChanged")).isEmpty();
    }

    @Dado("^que una maquinaria está Fuera de servicio o Inactiva$")
    public void aMachineIsOutOfServiceOrInactive() {
        anOutOfServiceAndAnInactiveMachineExist();
    }

    @Cuando("^un operario intenta utilizarla en una transformación$")
    public void anOperatorTriesToUseItInATransformation() {
        anOperatorTriesToUseThem();
    }

    @Entonces("^el sistema no permite seleccionarla\\.$")
    public void theSystemDoesNotAllowSelectingIt() {
        theTransformationIsRefused();
    }

    @Dado("^que la maquinaria está Fuera de servicio o Inactiva$")
    public void theMachineIsOutOfServiceOrInactive() {
        anOutOfServiceAndAnInactiveMachineExist();
    }

    @Cuando("^intenta seleccionarla$")
    public void triesToSelectIt() {
        anOperatorTriesToUseThem();
    }

    @Entonces("^el sistema no permite utilizarla\\.$")
    public void theSystemDoesNotAllowUsingIt() {
        theTransformationIsRefused();
    }
}
