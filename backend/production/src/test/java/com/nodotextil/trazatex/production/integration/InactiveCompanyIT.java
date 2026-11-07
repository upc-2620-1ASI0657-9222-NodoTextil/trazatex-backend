package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF-003 ("no podrán iniciar nuevas operaciones productivas"), con la decisión del usuario: con la empresa {@code INACTIVE}
 * se bloquean (409 {@code COMPANY_INACTIVE}) registrar, dividir, iniciar transformación, iniciar transferencia y aceptar una
 * transferencia entrante; se permiten finalizar una transformación ya iniciada y rechazar una transferencia entrante, para
 * que ningún lote quede atascado. Una empresa que no está en la proyección es {@code COMPANY_UNKNOWN}.
 */
class InactiveCompanyIT extends ApiTestBase {

    private void deactivate(UUID company) {
        saveCompany(company, CompanyStatus.INACTIVE);
    }

    private void activate(UUID company) {
        saveCompany(company, CompanyStatus.ACTIVE);
    }

    private static final List<Object> SPLIT = List.of(obj("quantityKg", 40), obj("quantityKg", 60));

    // ---- lo que se bloquea ----

    @Test
    void registeringIsBlocked() throws Exception {
        deactivate(COMPANY_A);

        assertThat(code(postAs(OPERATOR_OF_A, "/api/batches", batchBody(10)).andExpect(status().isConflict()))).isEqualTo("COMPANY_INACTIVE");
        assertThat(count("SELECT count(*) FROM batches")).isZero();
    }

    @Test
    void splittingIsBlocked() throws Exception {
        JsonNode batch = registerBatch(100);
        deactivate(COMPANY_A);

        assertThat(code(postAs(OPERATOR_OF_A, "/api/batches/" + batch.path("id").asString() + "/split", obj("children", SPLIT))
                .andExpect(status().isConflict()))).isEqualTo("COMPANY_INACTIVE");
        assertThat(count("SELECT count(*) FROM batches")).isEqualTo(1);
    }

    @Test
    void startingATransformationIsBlocked() throws Exception {
        JsonNode batch = registerBatch(100);
        UUID machine = idOf(registerMachine("MQ-01"));
        deactivate(COMPANY_A);

        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "SPINNING", "machineId", machine,
                "batchIds", List.of(idOf(batch)))).andExpect(status().isConflict()))).isEqualTo("COMPANY_INACTIVE");
        assertThat(count("SELECT count(*) FROM transformations")).isZero();
    }

    @Test
    void initiatingATransferIsBlocked() throws Exception {
        JsonNode batch = registerBatch(100);
        deactivate(COMPANY_A);

        assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch))))
                .andExpect(status().isConflict()))).isEqualTo("COMPANY_INACTIVE");
        assertThat(count("SELECT count(*) FROM transfers")).isZero();
    }

    @Test
    void acceptingAnIncomingTransferIsBlockedBecauseItBringsMaterialIn() throws Exception {
        JsonNode batch = registerBatch(100);
        String transfer = read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch))))
                .andExpect(status().isCreated())).path("id").asString();
        deactivate(COMPANY_B);

        assertThat(code(postAs(ADMIN_OF_B, "/api/transfers/" + transfer + "/accept").andExpect(status().isConflict()))).isEqualTo("COMPANY_INACTIVE");

        assertThat(count("SELECT count(*) FROM transfers WHERE status = 'PENDING'")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT company_id FROM batches WHERE id = :id").param("id", idOf(batch)).query(UUID.class).single()).isEqualTo(COMPANY_A);
    }

    // ---- lo que se permite, para que ningún lote quede atascado ----

    @Test
    void finishingATransformationAlreadyStartedIsAllowed() throws Exception {
        JsonNode batch = registerBatch(100);
        UUID machine = idOf(registerMachine("MQ-01"));
        UUID transformation = idOf(startTransformation("SPINNING", machine, idOf(batch)));
        deactivate(COMPANY_A);

        JsonNode result = read(postAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/complete",
                obj("outputs", List.of(output("Hilo", 90)))).andExpect(status().isOk()));

        assertThat(result.path("transformation").path("status").asString()).isEqualTo("COMPLETED");
        assertThat(count("SELECT count(*) FROM batches WHERE current_stage = 'PROCESSED'")).isEqualTo(1);
        // las salidas existen y siguen siendo suyas, pero con la empresa inactiva no se pueden operar
        String output = result.path("outputs").get(0).path("id").asString();
        assertThat(code(postAs(OPERATOR_OF_A, "/api/batches/" + output + "/split", obj("children", SPLIT)).andExpect(status().isConflict())))
                .isEqualTo("COMPANY_INACTIVE");
    }

    @Test
    void rejectingAnIncomingTransferIsAllowedSoTheBatchesGoBackToTheOrigin() throws Exception {
        JsonNode batch = registerBatch(100);
        String transfer = read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch))))
                .andExpect(status().isCreated())).path("id").asString();
        deactivate(COMPANY_B);

        JsonNode rejected = read(postAs(ADMIN_OF_B, "/api/transfers/" + transfer + "/reject", obj("reason", "Estamos inactivos"))
                .andExpect(status().isOk()));

        assertThat(rejected.path("status").asString()).isEqualTo("REJECTED");
        JsonNode back = read(getAs(OPERATOR_OF_A, "/api/batches/" + batch.path("id").asString()).andExpect(status().isOk()));
        assertThat(back.path("phase").asString()).isEqualTo("AVAILABLE");
        assertThat(back.path("companyId").asString()).isEqualTo(COMPANY_A.toString());
    }

    @Test
    void readingMachinesAndCommercialDataStayAvailable() throws Exception {
        JsonNode batch = registerBatch(100);
        UUID machine = idOf(registerMachine("MQ-01"));
        deactivate(COMPANY_A);

        getAs(OPERATOR_OF_A, "/api/batches").andExpect(status().isOk());
        getAs(OPERATOR_OF_A, "/api/batches/" + batch.path("id").asString()).andExpect(status().isOk());
        getAs(OPERATOR_OF_A, "/api/batches/qr/" + batch.path("qrCode").asString()).andExpect(status().isOk());
        getAs(ADMIN_OF_A, "/api/machines").andExpect(status().isOk());
        getAs(ADMIN_OF_A, "/api/transformations").andExpect(status().isOk());
        getAs(ADMIN_OF_A, "/api/transfers").andExpect(status().isOk());
        postAs(ADMIN_OF_A, "/api/machines", obj("internalCode", "MQ-02", "name", "Otra", "type", "Cardado")).andExpect(status().isCreated());
        patchAs(ADMIN_OF_A, "/api/machines/" + machine + "/status", obj("status", "INACTIVE")).andExpect(status().isOk());
    }

    // ---- reactivar y empresa desconocida ----

    @Test
    void reactivatingTheCompanyRestoresEveryOperation() throws Exception {
        deactivate(COMPANY_A);
        postAs(OPERATOR_OF_A, "/api/batches", batchBody(10)).andExpect(status().isConflict());

        activate(COMPANY_A);

        postAs(OPERATOR_OF_A, "/api/batches", batchBody(10)).andExpect(status().isCreated());
    }

    @Test
    void aCompanyThatIsNotInTheProjectionYetIsUnknownAndCannotStartWork() throws Exception {
        JsonNode batch = registerBatch(100);
        UUID machine = idOf(registerMachine("MQ-01"));
        UUID transformation = idOf(startTransformation("SPINNING", machine, idOf(batch)));
        jdbc.sql("DELETE FROM company_projection WHERE company_id = :id").param("id", COMPANY_A).update();

        assertThat(code(postAs(OPERATOR_OF_A, "/api/batches", batchBody(10)).andExpect(status().isConflict()))).isEqualTo("COMPANY_UNKNOWN");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transformations", obj("type", "SPINNING", "machineId", machine,
                "batchIds", List.of(UUID.randomUUID()))).andExpect(status().isConflict()))).isEqualTo("COMPANY_UNKNOWN");
        assertThat(code(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(batch))))
                .andExpect(status().isConflict()))).isEqualTo("COMPANY_UNKNOWN");
        // cerrar trabajo ya iniciado y consultar siguen permitidos
        postAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/complete", obj("outputs", List.of(output("Hilo", 90))))
                .andExpect(status().isOk());
        getAs(OPERATOR_OF_A, "/api/batches").andExpect(status().isOk());
    }
}
