package com.nodotextil.trazatex.organizationaccess.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US11. */
class PrivacyApiIT extends IntegrationTestBase {

    UUID company;
    UUID admin;

    @BeforeEach
    void company() {
        company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
    }

    private org.springframework.test.web.servlet.ResultActions update(String fieldsJson) throws Exception {
        return perform(body(asAdmin(PUT("/api/privacy-settings"), admin, company), "{\"fields\":" + fieldsJson + "}"));
    }

    @Test
    void everyConfigurableFieldIsPrivateByDefault() throws Exception {
        perform(asAdmin(GET("/api/privacy-settings"), admin, company))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields", aMapWithSize(11)))
                .andExpect(jsonPath("$.fields.SUPPLIER").value("PRIVATE"))
                .andExpect(jsonPath("$.fields.QUALITY_TEST_VALUES").value("PRIVATE"));
    }

    @Test
    void shareThenMakePrivateAgain() throws Exception {
        update("{\"SUPPLIER\":\"SHARED\",\"ORIGIN\":\"SHARED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.SUPPLIER").value("SHARED"))
                .andExpect(jsonPath("$.fields.QUANTITY").value("PRIVATE"));
        assertThat(jdbc.sql("SELECT visibility FROM privacy_settings WHERE field = 'SUPPLIER'").query(String.class).single()).isEqualTo("SHARED");

        update("{\"SUPPLIER\":\"PRIVATE\"}").andExpect(status().isOk());
        perform(asAdmin(GET("/api/privacy-settings"), admin, company))
                .andExpect(jsonPath("$.fields.SUPPLIER").value("PRIVATE"))
                .andExpect(jsonPath("$.fields.ORIGIN").value("SHARED"));
    }

    @Test
    void eachUpdatePublishesTheCompleteMap() throws Exception {
        update("{\"SUPPLIER\":\"SHARED\"}").andExpect(status().isOk());

        assertThat(outboxCount("CompanyPrivacyUpdated")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT topic FROM outbox WHERE event_type = 'CompanyPrivacyUpdated'").query(String.class).single()).isEqualTo("orgaccess.company");
        assertThat(jdbc.sql("SELECT count(*) FROM outbox, jsonb_object_keys(payload->'payload'->'fields') WHERE event_type = 'CompanyPrivacyUpdated'")
                .query(Long.class).single()).isEqualTo(11);
        assertThat(jdbc.sql("SELECT payload->'payload'->'fields'->>'SUPPLIER' FROM outbox WHERE event_type = 'CompanyPrivacyUpdated'")
                .query(String.class).single()).isEqualTo("SHARED");
    }

    @Test
    void alwaysSharedAndAlwaysPrivateInformationCannotBeConfiguredAndNothingIsApplied() throws Exception {
        for (String field : new String[]{"TRACEABILITY_ID", "RESPONSIBLE_COMPANY", "OPERATOR_IDENTITY", "NO_EXISTE"}) {
            update("{\"SUPPLIER\":\"SHARED\",\"" + field + "\":\"PRIVATE\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("FIELD_NOT_CONFIGURABLE"))
                    .andExpect(jsonPath("$.details[0]").value(field));
        }
        assertThat(count("SELECT count(*) FROM privacy_settings WHERE visibility = 'SHARED'")).isZero();
        assertThat(outboxCount("CompanyPrivacyUpdated")).isZero();
    }

    @Test
    void anInvalidVisibilityOrAnEmptyChangeIsABadRequest() throws Exception {
        update("{\"SUPPLIER\":\"MAYBE\"}").andExpect(status().isBadRequest());
        update("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_FIELDS"));
    }

    @Test
    void onlyTheCompanyAdminConfiguresItsPrivacy() throws Exception {
        UUID operator = insertUser(company, "Op", "op@hilos.pe", "OPERATOR", "ACTIVE");
        perform(body(as(PUT("/api/privacy-settings"), operator, company, "OPERATOR"), "{\"fields\":{\"SUPPLIER\":\"SHARED\"}}"))
                .andExpect(status().isForbidden());
        perform(body(asOwner(PUT("/api/privacy-settings")), "{\"fields\":{\"SUPPLIER\":\"SHARED\"}}")).andExpect(status().isForbidden());
    }

    @Test
    void anInactiveCompanyCanReadButNotChangeItsPrivacy() throws Exception {
        jdbc.sql("UPDATE companies SET status = 'INACTIVE' WHERE id = :id").param("id", company).update();
        update("{\"SUPPLIER\":\"SHARED\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"));
        perform(asAdmin(GET("/api/privacy-settings"), admin, company)).andExpect(status().isOk());
    }
}
