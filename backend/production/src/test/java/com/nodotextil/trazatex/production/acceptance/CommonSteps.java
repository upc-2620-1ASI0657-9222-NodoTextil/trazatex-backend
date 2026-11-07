package com.nodotextil.trazatex.production.acceptance;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los pasos {@code Cuando} que son simplemente "el usuario confirma": ejecutan la petición que los pasos {@code Dado} dejaron preparada.
 * Varios escenarios usan la misma frase para operaciones distintas (confirmar un registro de maquinaria o de lote), por eso viven aquí.
 */
public class CommonSteps extends StepSupport {

    public CommonSteps(World world, ProductionApi api, Outbox outbox, JdbcClient jdbc, ObjectMapper json) {
        super(world, api, outbox, jdbc, json);
    }

    /** Lo dicen US16 (la transformación, {@code startTime}) y US21 (la transferencia, {@code departureTime}): se lee de la respuesta recién obtenida. */
    @Entonces("^el sistema registra fecha y hora de inicio$")
    public void theSystemRegistersTheStartDateAndTime() {
        assertThat(world.last.status()).as("la operación debía crearse: %s", world.last.raw()).isEqualTo(201);
        String field = world.last.field("startTime") != null ? "startTime" : "departureTime";
        assertThat(world.last.field(field)).as("la respuesta debe traer " + field).isNotNull();
        assertWithinTheRequest(world.last.text(field), "la fecha y hora de inicio (" + field + ")");
        if (field.equals("startTime")) {
            assertThat(world.last.text("status")).isEqualTo("IN_PROGRESS");
            assertThat(world.last.text("operatorId")).isEqualTo(world.operatorA.userId().toString());
        } else {
            assertThat(world.last.text("status")).isEqualTo("PENDING");
            assertThat(world.last.text("initiatedBy")).isEqualTo(world.operatorA.userId().toString());
        }
    }

    @Cuando("^confirma el registro$")
    public void confirmsTheRegistration() {
        world.submit();
    }

    @Cuando("^registra el lote sin completar dicho campo$")
    public void registersTheBatchWithoutThatField() {
        world.submit();
    }

    @Cuando("^intenta registrar el lote$")
    public void triesToRegisterTheBatch() {
        world.submit();
    }

    @Cuando("^confirma la división$")
    public void confirmsTheSplit() {
        world.submit();
    }

    @Cuando("^intenta confirmar la división$")
    public void triesToConfirmTheSplit() {
        world.submit();
    }

    @Cuando("^confirma el inicio$")
    public void confirmsTheStart() {
        world.submit();
    }

    @Cuando("^intenta iniciar la transformación$")
    public void triesToStartTheTransformation() {
        world.submit();
    }

    @Cuando("^se generan sus lotes de salida$")
    public void itsOutputBatchesAreGenerated() {
        world.submit();
    }

    @Cuando("^el sistema procesa las cantidades aportadas$")
    public void theSystemProcessesTheQuantities() {
        world.submit();
    }

    @Cuando("^solicita finalizarla$")
    public void asksToFinishIt() {
        world.submit();
    }

    @Cuando("^intenta finalizar$")
    public void triesToFinish() {
        world.submit();
    }

    @Cuando("^completa la información$")
    public void completesTheInformation() {
        world.submit();
    }

    @Cuando("^intenta guardar sin indicar moneda$")
    public void triesToSaveWithoutCurrency() {
        world.submit();
    }

    @Cuando("^confirma la transferencia$")
    public void confirmsTheTransfer() {
        world.submit();
    }

    @Cuando("^intenta iniciar la transferencia$")
    public void triesToStartTheTransfer() {
        world.submit();
    }

    @Cuando("^confirma el rechazo$")
    public void confirmsTheRejection() {
        world.submit();
    }

    @Cuando("^el operario intenta adjuntarlo$")
    public void theOperatorTriesToAttachIt() {
        world.submit();
    }
}
