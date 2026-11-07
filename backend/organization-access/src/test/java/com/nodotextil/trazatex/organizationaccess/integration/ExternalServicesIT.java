package com.nodotextil.trazatex.organizationaccess.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.RUC;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.forRuc;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.json;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.notFound;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.problem;
import static com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaStubs.required;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los adaptadores reales de Chequea.pe (ADR-012) y HIBP (ADR-007) dentro de los casos de uso, contra WireMock. */
class ExternalServicesIT extends IntegrationTestBase {

    @BeforeEach
    void realServices() {
        useRealExternalServices();
    }

    private static String createBody(String ruc) {
        return """
                {"ruc":"%s","businessName":"SUNAT Prueba","activities":["WEAVING"],"adminEmail":"admin@sunat.pe"}
                """.formatted(ruc);
    }

    private org.springframework.test.web.servlet.ResultActions createCompany(String ruc) throws Exception {
        return perform(body(asOwner(POST("/api/companies")), createBody(ruc)));
    }

    private long chequeaCalls() {
        return WIREMOCK.countRequestsMatching(getRequestedFor(urlEqualTo("/api/v1/ruc/" + RUC)).build()).getCount();
    }

    // ---- US02: RUC con Chequea.pe ----

    @Test
    void anActiveAndHabidoRucCreatesTheCompanyAndIsCached() throws Exception {
        WIREMOCK.stubFor(forRuc(RUC).willReturn(json(200, required("ruc-activo-habido.json"))));

        createCompany(RUC).andExpect(status().isCreated()).andExpect(jsonPath("$.company.ruc").value(RUC));

        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/api/v1/ruc/" + RUC)).withHeader("Authorization", equalTo("Bearer " + TEST_CHEQUEA_TOKEN)));
        assertThat(jdbc.sql("SELECT taxpayer_status || '/' || domicile_condition || '/' || business_name FROM validated_ruc_cache WHERE ruc = :r")
                .param("r", RUC).query(String.class).single()).startsWith("ACTIVO/HABIDO/SUPERINTENDENCIA NACIONAL DE ADUANAS");
    }

    @Test
    void bajaAndNoHabidoRucsAreRejectedWithoutCreatingAnything() throws Exception {
        for (String file : new String[]{"ruc-baja.json", "ruc-no-habido.json"}) {
            WIREMOCK.stubFor(forRuc(RUC).willReturn(json(200, required(file))));

            createCompany(RUC).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RUC_INVALID"));
        }
        assertThat(count("SELECT count(*) FROM companies")).isZero();
        assertThat(count("SELECT count(*) FROM validated_ruc_cache")).isZero();
        assertThat(count("SELECT count(*) FROM outbox")).isZero();
    }

    @Test
    void aNonExistentRucAndAnApiFormatErrorAreRejected() throws Exception {
        WIREMOCK.stubFor(forRuc(RUC).willReturn(notFound()));
        createCompany(RUC).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RUC_INVALID"));

        WIREMOCK.stubFor(forRuc(RUC).willReturn(problem(400, required("ruc-formato-invalido.json"))));
        createCompany(RUC).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RUC_INVALID"));

        assertThat(count("SELECT count(*) FROM companies")).isZero();
    }

    @Test
    void aRucWithAnInvalidPrefixIsRejectedLocallyWithoutCallingChequea() throws Exception {
        String prefix = "3010007097";
        String badPrefix = prefix + com.nodotextil.trazatex.organizationaccess.domain.model.Ruc.checkDigitFor(prefix);

        createCompany(badPrefix).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RUC_INVALID"));

        assertThat(WIREMOCK.getAllServeEvents()).isEmpty();
    }

    @Test
    void whenChequeaIsDownAndThereIsNoCacheTheCompanyIsRejectedWithARetryMessage() throws Exception {
        WIREMOCK.stubFor(forRuc(RUC).willReturn(aResponse().withStatus(503)));

        createCompany(RUC).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RUC_VALIDATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Inténtalo más tarde")));

        assertThat(count("SELECT count(*) FROM companies")).isZero();
    }

    @Test
    void aTimeoutIsTreatedAsUnavailable() throws Exception {
        WIREMOCK.stubFor(forRuc(RUC).willReturn(json(200, required("ruc-activo-habido.json")).withFixedDelay(3000)));

        long start = System.nanoTime();
        createCompany(RUC).andExpect(status().isServiceUnavailable());

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2500));
        assertThat(count("SELECT count(*) FROM companies")).isZero();
    }

    @Test
    void aCachedValidationLetsTheCompanyBeCreatedWhenChequeaIsDown() throws Exception {
        jdbc.sql("INSERT INTO validated_ruc_cache VALUES (:r, 'SUNAT', 'ACTIVO', 'HABIDO', :at)")
                .param("r", RUC).param("at", utc(clock.instant().minus(Duration.ofDays(5)))).update();   // antigua: se intenta la API primero
        WIREMOCK.stubFor(forRuc(RUC).willReturn(aResponse().withStatus(500)));

        createCompany(RUC).andExpect(status().isCreated());

        assertThat(chequeaCalls()).isEqualTo(1);
    }

    @Test
    void aFreshCachedValidationIsUsedWithoutCallingTheApi() throws Exception {
        WIREMOCK.stubFor(forRuc(RUC).willReturn(json(200, required("ruc-activo-habido.json"))));
        createCompany(RUC).andExpect(status().isCreated());
        jdbc.sql("TRUNCATE companies, company_activities, invitations, privacy_settings, users CASCADE").update();
        ownerId = insertUser(null, "OWNER", OWNER_EMAIL, "LICENSE_OWNER", "ACTIVE");
        clock.advance(Duration.ofHours(2));

        createCompany(RUC).andExpect(status().isCreated());

        assertThat(chequeaCalls()).as("la segunda creación usa la caché").isEqualTo(1);
    }

    @Test
    void theCompanyLimitIsCheckedBeforeCallingChequea() throws Exception {
        insertCompany("20100070970", "Una SAC", "ACTIVE");
        insertCompany("20100047218", "Dos SAC", "ACTIVE");
        insertCompany("20512528458", "Tres SAC", "ACTIVE");

        createCompany(RUC).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPANY_LIMIT_REACHED"));

        assertThat(chequeaCalls()).isZero();
    }

    @Test
    void repeatedFailuresOpenTheCircuitAndTheApiIsNotCalledAnymore() throws Exception {
        WIREMOCK.stubFor(forRuc(RUC).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 8; i++) {
            createCompany(RUC).andExpect(status().isServiceUnavailable());
        }

        assertThat(chequeaCalls()).as("por defecto abre tras 5 llamadas fallidas").isEqualTo(5);
    }

    // ---- US04: HIBP ----

    private String pendingOperatorToken() {
        UUID company = insertCompany("20100070970", "Hilos SAC", "ACTIVE");
        UUID admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
        return tokenOf(insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START));
    }

    private org.springframework.test.web.servlet.ResultActions accept(String token, String password) throws Exception {
        return perform(body(POST("/api/invitations/" + token + "/accept"),
                "{\"firstName\":\"Luis\",\"lastName\":\"Rojas\",\"password\":\"%s\",\"passwordConfirmation\":\"%s\"}".formatted(password, password)));
    }

    @Test
    void aLeakedPasswordIsRejectedAndOnlyThePrefixLeavesTheService() throws Exception {
        String token = pendingOperatorToken();
        WIREMOCK.stubFor(get(urlEqualTo("/range/5BAA6")).willReturn(aResponse().withStatus(200)
                .withBody("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n1E4C9B93F3F0682250B6CF8331B7EE68FD8:3861493\r\n")));

        accept(token, "password").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_COMPROMISED"));

        assertThat(count("SELECT count(*) FROM users WHERE email = 'luis@hilos.pe'")).isZero();
        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/range/5BAA6")).withHeader("Add-Padding", equalTo("true")));
        assertThat(WIREMOCK.getAllServeEvents()).allSatisfy(event ->
                assertThat(event.getRequest().getUrl() + event.getRequest().getBodyAsString()).doesNotContain("password").doesNotContain("1E4C9B93F3"));
    }

    @Test
    void aCleanPasswordCreatesTheAccountWithoutAudit() throws Exception {
        String token = pendingOperatorToken();
        WIREMOCK.stubFor(get(urlEqualTo("/range/5BAA6")).willReturn(aResponse().withStatus(200).withBody("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n")));

        accept(token, "password").andExpect(status().isCreated());

        assertThat(count("SELECT count(*) FROM audit_log")).isZero();
    }

    @Test
    void ifHibpIsDownTheAccountIsCreatedAndAWarningIsAudited() throws Exception {
        String token = pendingOperatorToken();
        WIREMOCK.stubFor(get(urlEqualTo("/range/5BAA6")).willReturn(aResponse().withStatus(500)));

        accept(token, "password").andExpect(status().isCreated());

        assertThat(jdbc.sql("SELECT action FROM audit_log").query(String.class).single()).isEqualTo("PASSWORD_BREACH_CHECK_SKIPPED");
        assertThat(count("SELECT count(*) FROM users WHERE email = 'luis@hilos.pe'")).isEqualTo(1);
    }

    @Test
    void ifHibpTimesOutTheAccountIsCreatedWithinTheTimeout() throws Exception {
        String token = pendingOperatorToken();
        WIREMOCK.stubFor(get(urlEqualTo("/range/5BAA6")).willReturn(aResponse().withStatus(200).withBody("1E4C9B93F3F0682250B6CF8331B7EE68FD8:9").withFixedDelay(3000)));

        long start = System.nanoTime();
        accept(token, "password").andExpect(status().isCreated());

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2500));
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'PASSWORD_BREACH_CHECK_SKIPPED'")).isEqualTo(1);
    }
}
