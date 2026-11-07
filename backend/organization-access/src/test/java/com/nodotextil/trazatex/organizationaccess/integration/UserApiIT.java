package com.nodotextil.trazatex.organizationaccess.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US09. */
class UserApiIT extends IntegrationTestBase {

    UUID company;
    UUID admin;

    @BeforeEach
    void company() {
        company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
    }

    private org.springframework.test.web.servlet.ResultActions setStatus(UUID user, String status) throws Exception {
        return perform(body(asAdmin(PATCH("/api/users/" + user + "/status"), admin, company), "{\"status\":\"" + status + "\"}"));
    }

    @Test
    void adminListsOnlyTheUsersOfItsOwnCompany() throws Exception {
        insertUser(company, "Op", "op@hilos.pe", "OPERATOR", "ACTIVE");
        UUID other = insertCompany(OTHER_RUC, "Telas SAC", "ACTIVE");
        insertUser(other, "Ajeno", "ajeno@telas.pe", "COMPANY_ADMIN", "ACTIVE");

        perform(asAdmin(GET("/api/users"), admin, company))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void adminInactivatesAnActiveUserKeepingTheHistory() throws Exception {
        UUID operator = insertUser(company, "Op", "op@hilos.pe", "OPERATOR", "ACTIVE");

        setStatus(operator, "INACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

        assertThat(count("SELECT count(*) FROM users WHERE id = ?", operator)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT changes->>'from' || '>' || (changes->>'to') FROM audit_log WHERE action = 'USER_STATUS_CHANGED'")
                .query(String.class).single()).isEqualTo("ACTIVE>INACTIVE");
        assertThat(jdbc.sql("SELECT payload->'payload'->>'status' FROM outbox WHERE event_type = 'UserAccountUpdated'")
                .query(String.class).single()).isEqualTo("INACTIVE");
    }

    @Test
    void theLastActiveAdministratorCannotBeInactivated() throws Exception {
        setStatus(admin, "INACTIVE").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
        assertThat(jdbc.sql("SELECT status FROM users WHERE id = :id").param("id", admin).query(String.class).single()).isEqualTo("ACTIVE");

        UUID second = insertUser(company, "Bea", "bea@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
        setStatus(second, "INACTIVE").andExpect(status().isOk());
        setStatus(admin, "INACTIVE").andExpect(status().isConflict());
    }

    @Test
    void adminReactivatesAnInactiveUserWhenThereIsCapacity() throws Exception {
        UUID operator = insertUser(company, "Op", "op@hilos.pe", "OPERATOR", "INACTIVE");

        setStatus(operator, "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void reactivationWithoutCapacityIsRejected() throws Exception {
        UUID operator = insertUser(company, "Op", "op@hilos.pe", "OPERATOR", "INACTIVE");
        insertInvitation(company, "uno@hilos.pe", "OPERATOR", "PENDING", admin, START);
        insertInvitation(company, "dos@hilos.pe", "OPERATOR", "PENDING", admin, START);

        setStatus(operator, "ACTIVE").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_LIMIT_REACHED"));
        assertThat(jdbc.sql("SELECT status FROM users WHERE id = :id").param("id", operator).query(String.class).single()).isEqualTo("INACTIVE");
    }

    @Test
    void settingTheCurrentStatusAgainConflicts() throws Exception {
        UUID operator = insertUser(company, "Op", "op@hilos.pe", "OPERATOR", "ACTIVE");
        setStatus(operator, "ACTIVE").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void aUserOfAnotherCompanyIsNotVisible() throws Exception {
        UUID other = insertCompany(OTHER_RUC, "Telas SAC", "ACTIVE");
        UUID foreign = insertUser(other, "Ajeno", "ajeno@telas.pe", "OPERATOR", "ACTIVE");

        setStatus(foreign, "INACTIVE").andExpect(status().isNotFound());
        setStatus(UUID.randomUUID(), "INACTIVE").andExpect(status().isNotFound());
    }

    @Test
    void statusIsRequired() throws Exception {
        UUID operator = insertUser(company, "Op", "op@hilos.pe", "OPERATOR", "ACTIVE");
        perform(body(asAdmin(PATCH("/api/users/" + operator + "/status"), admin, company), "{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_FIELDS"));
    }

    @Test
    void operatorsCannotManageUsersAndInactiveCompaniesCannotWrite() throws Exception {
        UUID operator = insertUser(company, "Op", "op@hilos.pe", "OPERATOR", "ACTIVE");
        perform(body(as(PATCH("/api/users/" + operator + "/status"), operator, company, "OPERATOR"), "{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());

        jdbc.sql("UPDATE companies SET status = 'INACTIVE' WHERE id = :id").param("id", company).update();
        setStatus(operator, "INACTIVE").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"));
    }
}
