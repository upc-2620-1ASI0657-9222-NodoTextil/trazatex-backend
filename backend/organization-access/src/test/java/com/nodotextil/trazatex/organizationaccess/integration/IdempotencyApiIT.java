package com.nodotextil.trazatex.organizationaccess.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR-010 con el filtro del shared-kernel montado sobre los endpoints reales y PostgreSQL. */
class IdempotencyApiIT extends IntegrationTestBase {

    UUID company;
    UUID admin;

    @BeforeEach
    void company() {
        company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
    }

    private static String operatorBody(String email) {
        return "{\"firstName\":\"Luis\",\"lastName\":\"Rojas\",\"email\":\"" + email + "\",\"position\":\"Tejedor\"}";
    }

    private org.springframework.test.web.servlet.ResultActions invite(String key, String email) throws Exception {
        return perform(body(asAdmin(POST("/api/invitations/operators"), admin, company), operatorBody(email)).header("Idempotency-Key", key));
    }

    @Test
    void theSameKeyDoesNotCreateTwoInvitationsNorTwoEventsAndReplaysTheResponse() throws Exception {
        String key = UUID.randomUUID().toString();

        String first = invite(key, "luis@hilos.pe").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = invite(key, "luis@hilos.pe").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(second)).isEqualTo(json.readTree(first));
        assertThat(json.readTree(second).get("id").asString()).isNotEmpty();
        assertThat(count("SELECT count(*) FROM invitations WHERE email = 'luis@hilos.pe'")).isEqualTo(1);
        assertThat(outboxCount("InvitationCreated")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM idempotency_keys WHERE response_status = 201")).isEqualTo(1);
    }

    @Test
    void theSameKeyWithADifferentBodyIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        invite(key, "luis@hilos.pe").andExpect(status().isCreated());

        invite(key, "otro@hilos.pe").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(count("SELECT count(*) FROM invitations WHERE email = 'otro@hilos.pe'")).isZero();
    }

    @Test
    void aFailedRequestLeavesNoTraceSoTheSameKeyCanBeRetried() throws Exception {
        insertInvitation(company, "uno@hilos.pe", "OPERATOR", "PENDING", admin, START);
        insertInvitation(company, "dos@hilos.pe", "OPERATOR", "PENDING", admin, START);
        String key = UUID.randomUUID().toString();

        invite(key, "luis@hilos.pe").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_LIMIT_REACHED"));
        assertThat(count("SELECT count(*) FROM idempotency_keys")).isZero();
        assertThat(count("SELECT count(*) FROM outbox")).isZero();

        jdbc.sql("UPDATE license SET max_users_per_company = 4").update();
        invite(key, "luis@hilos.pe").andExpect(status().isCreated());
        assertThat(count("SELECT count(*) FROM invitations WHERE email = 'luis@hilos.pe'")).isEqualTo(1);
    }

    @Test
    void companyCreationIsIdempotentToo() throws Exception {
        String key = UUID.randomUUID().toString();
        String request = "{\"ruc\":\"" + OTHER_RUC + "\",\"businessName\":\"Nueva SAC\",\"activities\":[\"SPINNING\"],\"adminEmail\":\"a@nueva.pe\"}";
        perform(body(asOwner(POST("/api/companies")), request).header("Idempotency-Key", key)).andExpect(status().isCreated());
        perform(body(asOwner(POST("/api/companies")), request).header("Idempotency-Key", key)).andExpect(status().isCreated());

        assertThat(count("SELECT count(*) FROM companies WHERE ruc = ?", OTHER_RUC)).isEqualTo(1);
        assertThat(outboxCount("CompanyRegistered")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM invitations WHERE email = 'a@nueva.pe'")).isEqualTo(1);
    }

    @Test
    void aWriteWithAKeyButWithoutAUserIdIsRejected() throws Exception {
        perform(body(POST("/api/invitations/operators"), operatorBody("luis@hilos.pe")).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_USER_ID"));
    }
}
