package com.nodotextil.trazatex.production.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US10. */
class MachineApiIT extends ApiTestBase {

    @Test
    void anAdministratorRegistersAnActiveMachine() throws Exception {
        JsonNode machine = registerMachine(" MQ-07 ");

        assertThat(machine.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(machine.path("internalCode").asString()).isEqualTo("MQ-07");
        assertThat(machine.path("companyId").asString()).isEqualTo(COMPANY_A.toString());
        assertThat(machine.path("name").asString()).isEqualTo("Cardadora");
        assertThat(machine.path("type").asString()).isEqualTo("Cardado");
        assertThat(count("SELECT count(*) FROM machines")).isEqualTo(1);
    }

    @Test
    void codeNameAndTypeAreRequired() throws Exception {
        ResultActions result = postAs(ADMIN_OF_A, "/api/machines", obj("internalCode", "MQ-1")).andExpect(status().isBadRequest());

        assertThat(code(result)).isEqualTo("MISSING_FIELDS");
        List<String> details = new ArrayList<>();
        read(result).path("details").forEach(d -> details.add(d.asString()));
        assertThat(details).containsExactlyInAnyOrder("name", "type");
        assertThat(count("SELECT count(*) FROM machines")).isZero();
    }

    @Test
    void theCodeIsUniquePerCompanyIgnoringCase() throws Exception {
        registerMachine("MQ-01");

        assertThat(code(postAs(ADMIN_OF_A, "/api/machines", obj("internalCode", "mq-01", "name", "Otra", "type", "Cardado"))
                .andExpect(status().isConflict()))).isEqualTo("MACHINE_CODE_ALREADY_EXISTS");
        postAs(ADMIN_OF_B, "/api/machines", obj("internalCode", "MQ-01", "name", "De B", "type", "Cardado")).andExpect(status().isCreated());
        assertThat(count("SELECT count(*) FROM machines")).isEqualTo(2);
    }

    @Test
    void theStatusChangesAmongTheThreeStatesAndTheHistoryIsKept() throws Exception {
        UUID id = idOf(registerMachine("MQ-01"));
        clock.advance(Duration.ofMinutes(5));
        patchAs(ADMIN_OF_A, "/api/machines/" + id + "/status", obj("status", "OUT_OF_SERVICE")).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(5));
        patchAs(ADMIN_OF_A, "/api/machines/" + id + "/status", obj("status", "INACTIVE")).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(5));
        JsonNode back = read(patchAs(ADMIN_OF_A, "/api/machines/" + id + "/status", obj("status", "ACTIVE")).andExpect(status().isOk()));

        assertThat(back.path("status").asString()).isEqualTo("ACTIVE");
        JsonNode detail = read(getAs(OPERATOR_OF_A, "/api/machines/" + id).andExpect(status().isOk()));
        assertThat(detail.path("machine").path("status").asString()).isEqualTo("ACTIVE");
        JsonNode history = detail.path("history");
        assertThat(history).hasSize(4);
        assertThat(history.get(0).path("from").isNull()).as("el registro no tiene estado anterior").isTrue();
        assertThat(history.get(0).path("to").asString()).isEqualTo("ACTIVE");
        assertThat(history.get(1).path("from").asString()).isEqualTo("ACTIVE");
        assertThat(history.get(1).path("to").asString()).isEqualTo("OUT_OF_SERVICE");
        assertThat(history.get(2).path("to").asString()).isEqualTo("INACTIVE");
        assertThat(history.get(3).path("to").asString()).isEqualTo("ACTIVE");
        assertThat(history.get(3).path("changedBy").asString()).isEqualTo(ADMIN_A.toString());
        assertThat(history.get(3).path("changedAt").asString()).isEqualTo(START.plus(Duration.ofMinutes(15)).toString());
    }

    @Test
    void repeatingTheCurrentStatusOrSendingAnUnknownOneIsRejected() throws Exception {
        UUID id = idOf(registerMachine("MQ-01"));

        assertThat(code(patchAs(ADMIN_OF_A, "/api/machines/" + id + "/status", obj("status", "ACTIVE")).andExpect(status().isConflict())))
                .isEqualTo("INVALID_STATUS_TRANSITION");
        patchAs(ADMIN_OF_A, "/api/machines/" + id + "/status", obj("status", "BROKEN")).andExpect(status().isBadRequest());
        assertThat(code(patchAs(ADMIN_OF_A, "/api/machines/" + id + "/status", obj()).andExpect(status().isBadRequest()))).isEqualTo("MISSING_FIELDS");
        assertThat(count("SELECT count(*) FROM machine_status_history WHERE machine_id = ?", id)).isEqualTo(1);
    }

    @Test
    void listsTheMachinesOfTheCompanyWithAnOptionalStatusFilter() throws Exception {
        registerMachine("B-2");
        UUID a = idOf(registerMachine("A-1"));
        patchAs(ADMIN_OF_A, "/api/machines/" + a + "/status", obj("status", "INACTIVE")).andExpect(status().isOk());
        postAs(ADMIN_OF_B, "/api/machines", obj("internalCode", "Z-9", "name", "De B", "type", "Cardado")).andExpect(status().isCreated());

        List<String> all = codes(read(getAs(OPERATOR_OF_A, "/api/machines").andExpect(status().isOk())));
        List<String> active = codes(read(getAs(ADMIN_OF_A, "/api/machines?status=ACTIVE").andExpect(status().isOk())));
        List<String> inactive = codes(read(getAs(ADMIN_OF_A, "/api/machines?status=INACTIVE").andExpect(status().isOk())));

        assertThat(all).containsExactly("A-1", "B-2");
        assertThat(active).containsExactly("B-2");
        assertThat(inactive).containsExactly("A-1");
        getAs(ADMIN_OF_A, "/api/machines?status=BROKEN").andExpect(status().isBadRequest());
    }

    @Test
    void aMachineOfAnotherCompanyDoesNotExist() throws Exception {
        UUID id = idOf(registerMachine("MQ-01"));

        getAs(OPERATOR_OF_B, "/api/machines/" + id).andExpect(status().isNotFound());
        assertThat(code(patchAs(ADMIN_OF_B, "/api/machines/" + id + "/status", obj("status", "INACTIVE")).andExpect(status().isNotFound())))
                .isEqualTo("MACHINE_NOT_FOUND");
        getAs(OPERATOR_OF_A, "/api/machines/" + UUID.randomUUID()).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM machines WHERE status = 'ACTIVE'")).isEqualTo(1);
    }

    private static List<String> codes(JsonNode list) {
        List<String> codes = new ArrayList<>();
        list.forEach(machine -> codes.add(machine.path("internalCode").asString()));
        return codes;
    }
}
