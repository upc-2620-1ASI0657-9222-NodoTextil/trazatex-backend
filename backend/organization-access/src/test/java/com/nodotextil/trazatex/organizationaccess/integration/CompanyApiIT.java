package com.nodotextil.trazatex.organizationaccess.integration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US01, US02, US03. */
class CompanyApiIT extends IntegrationTestBase {

    private static String createBody(String ruc, String name, String adminEmail) {
        return """
                {"ruc":"%s","businessName":"%s","activities":["WEAVING","DYEING"],"adminEmail":"%s"}
                """.formatted(ruc, name, adminEmail);
    }

    @Test
    void ownerCreatesACompanyAndInvitesItsFirstAdministrator() throws Exception {
        perform(body(asOwner(POST("/api/companies")), createBody(VALID_RUC, "Hilos del Sur SAC", "admin@hilos.pe")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company.ruc").value(VALID_RUC))
                .andExpect(jsonPath("$.company.status").value("ACTIVE"))
                .andExpect(jsonPath("$.company.activities", containsInAnyOrder("WEAVING", "DYEING")))
                .andExpect(jsonPath("$.adminInvitationId").exists());

        assertThat(jdbc.sql("SELECT role || '/' || status || '/' || issued_by FROM invitations WHERE email = 'admin@hilos.pe'")
                .query(String.class).single()).isEqualTo("COMPANY_ADMIN/PENDING/" + ownerId);
        assertThat(count("SELECT count(*) FROM privacy_settings WHERE visibility = 'PRIVATE'")).isEqualTo(11);
        assertThat(outboxCount("CompanyRegistered")).isEqualTo(1);
        assertThat(outboxCount("CompanyPrivacyUpdated")).isEqualTo(1);
        assertThat(outboxCount("InvitationCreated")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT payload->'payload'->>'acceptUrl' FROM outbox WHERE event_type = 'InvitationCreated'")
                .query(String.class).single()).startsWith("http://localhost:5173/invitations/");
        assertThat(jdbc.sql("SELECT topic FROM outbox WHERE event_type = 'CompanyRegistered'").query(String.class).single())
                .isEqualTo("orgaccess.company");
    }

    @Test
    void creationIsRejectedWhenTheCompanyLimitIsReached() throws Exception {
        insertCompany(VALID_RUC, "Una SAC", "ACTIVE");
        insertCompany(OTHER_RUC, "Dos SAC", "INACTIVE");
        insertCompany(THIRD_RUC, "Tres SAC", "ACTIVE");

        perform(body(asOwner(POST("/api/companies")), createBody("20512528458", "Cuatro SAC", "a@cuatro.pe")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_LIMIT_REACHED"));

        assertThat(count("SELECT count(*) FROM companies")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM outbox")).isZero();
    }

    @Test
    void rucWithAWrongCheckDigitIsRejectedBeforeCallingChequea() throws Exception {
        perform(body(asOwner(POST("/api/companies")), createBody("20100070971", "Hilos SAC", "a@hilos.pe")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RUC_INVALID"));
        assertThat(count("SELECT count(*) FROM companies")).isZero();
    }

    @Test
    void rucThatIsNotActiveAndHabidoIsRejected() throws Exception {
        rucValidator.rejected.add(VALID_RUC);

        perform(body(asOwner(POST("/api/companies")), createBody(VALID_RUC, "Hilos SAC", "a@hilos.pe")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RUC_INVALID"));

        assertThat(count("SELECT count(*) FROM companies")).isZero();
        assertThat(count("SELECT count(*) FROM outbox")).isZero();
    }

    @Test
    void ifTheValidatorIsDownTheCompanyIsNotCreated() throws Exception {
        rucValidator.unavailable.set(true);

        perform(body(asOwner(POST("/api/companies")), createBody(VALID_RUC, "Hilos SAC", "a@hilos.pe")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RUC_VALIDATION_UNAVAILABLE"));

        assertThat(count("SELECT count(*) FROM companies")).isZero();
    }

    @Test
    void duplicateRucIsRejected() throws Exception {
        insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");

        perform(body(asOwner(POST("/api/companies")), createBody(VALID_RUC, "Otra SAC", "a@otra.pe")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RUC_ALREADY_REGISTERED"));
    }

    @Test
    void missingDataIsListed() throws Exception {
        perform(body(asOwner(POST("/api/companies")), "{\"ruc\":\"" + VALID_RUC + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_FIELDS"))
                .andExpect(jsonPath("$.details", containsInAnyOrder("businessName", "activities", "adminEmail")));
    }

    @Test
    void malformedJsonIsABadRequestWithApiError() throws Exception {
        perform(body(asOwner(POST("/api/companies")), "{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void onlyTheOwnerManagesCompanies() throws Exception {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        UUID admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");

        perform(body(asAdmin(POST("/api/companies"), admin, company), createBody(OTHER_RUC, "Otra SAC", "a@otra.pe")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        perform(asAdmin(GET("/api/companies"), admin, company)).andExpect(status().isForbidden());
    }

    @Test
    void requestsWithoutIdentityOrWithAnInconsistentOneAreUnauthorized() throws Exception {
        perform(GET("/api/companies")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        perform(GET("/api/companies").header("X-User-Id", "no-es-uuid").header("X-User-Role", "LICENSE_OWNER"))
                .andExpect(status().isUnauthorized());
        perform(GET("/api/companies").header("X-User-Id", ownerId.toString()).header("X-User-Role", "COMPANY_ADMIN"))
                .andExpect(status().isUnauthorized());
        perform(GET("/api/companies").header("X-User-Id", ownerId.toString()).header("X-User-Role", "SUPERUSER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerListsCompanies() throws Exception {
        insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        insertCompany(OTHER_RUC, "Telas SAC", "INACTIVE");

        perform(asOwner(GET("/api/companies"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void ownerInactivatesAndReactivatesACompanyKeepingItsData() throws Exception {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");

        perform(body(asOwner(PATCH("/api/companies/" + company + "/status")), "{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

        assertThat(jdbc.sql("SELECT status FROM companies WHERE id = :id").param("id", company).query(String.class).single()).isEqualTo("INACTIVE");
        assertThat(count("SELECT count(*) FROM users WHERE company_id = ?", company)).isEqualTo(1);
        assertThat(outboxCount("CompanyStatusChanged")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT payload->'payload'->>'status' FROM outbox WHERE event_type = 'CompanyStatusChanged'").query(String.class).single())
                .isEqualTo("INACTIVE");
        assertThat(jdbc.sql("SELECT changes->>'from' || '>' || (changes->>'to') FROM audit_log WHERE action = 'COMPANY_STATUS_CHANGED'")
                .query(String.class).single()).isEqualTo("ACTIVE>INACTIVE");

        perform(body(asOwner(PATCH("/api/companies/" + company + "/status")), "{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        perform(body(asOwner(PATCH("/api/companies/" + company + "/status")), "{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(outboxCount("CompanyStatusChanged")).isEqualTo(2);
    }

    @Test
    void changingTheStatusOfAnUnknownCompanyIs404() throws Exception {
        perform(body(asOwner(PATCH("/api/companies/" + UUID.randomUUID() + "/status")), "{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void licenseSummaryShowsLimitsCompaniesAndUsedSlotsPerCompany() throws Exception {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        UUID admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
        insertUser(company, "Inactivo", "inactivo@hilos.pe", "OPERATOR", "INACTIVE");
        insertInvitation(company, "pendiente@hilos.pe", "OPERATOR", "PENDING", admin, START);
        insertInvitation(company, "cancelada@hilos.pe", "OPERATOR", "CANCELLED", admin, START);
        insertInvitation(company, "vencida@hilos.pe", "OPERATOR", "PENDING", admin, START.minus(java.time.Duration.ofDays(8)));

        perform(asOwner(GET("/api/license")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxCompanies").value(3))
                .andExpect(jsonPath("$.maxUsersPerCompany").value(3))
                .andExpect(jsonPath("$.companiesUsed").value(1))
                .andExpect(jsonPath("$.companiesAvailable").value(2))
                .andExpect(jsonPath("$.companies[0].businessName").value("Hilos SAC"))
                .andExpect(jsonPath("$.companies[0].usedSlots").value(2))
                .andExpect(jsonPath("$.companies[0].availableSlots").value(1));

        perform(asAdmin(GET("/api/license"), admin, company)).andExpect(status().isForbidden());
    }
}
