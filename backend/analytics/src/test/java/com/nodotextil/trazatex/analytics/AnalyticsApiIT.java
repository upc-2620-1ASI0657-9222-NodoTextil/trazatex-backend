package com.nodotextil.trazatex.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** M1 y M2: cada error HTTP es un {@code ApiError} con un código del contrato; el periodo tiene tope; la ruta es {@code /summary} (I6). */
class AnalyticsApiIT extends AnalyticsIntegrationTest {
    final UUID user = UUID.randomUUID(), company = UUID.randomUUID();
    static final String OK = "/api/analytics/summary?from=2026-10-01&to=2026-10-31";

    private JsonNode error(ResponseEntity<String> response, int status, String code) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.path("code").asString()).isEqualTo(code);
        assertThat(body.path("message").asString()).isNotBlank();
        assertThat(body.has("timestamp")).isTrue();
        assertThat(response.getHeaders().getFirst("Cache-Control")).contains("no-store");
        return body;
    }

    @Test void withoutAValidIdentityItIsAuthenticationRequired() {
        error(get(OK, null, null, null), 401, "AUTHENTICATION_REQUIRED");
        error(get(OK, "no-es-un-uuid", "COMPANY_ADMIN", company), 401, "AUTHENTICATION_REQUIRED");
        error(get(OK, user.toString(), "ROL_RARO", company), 401, "AUTHENTICATION_REQUIRED");
        error(get(OK, user.toString(), "COMPANY_ADMIN", null), 401, "AUTHENTICATION_REQUIRED");      // administrador sin empresa
        error(get(OK, user.toString(), "LICENSE_OWNER", company), 401, "AUTHENTICATION_REQUIRED");   // dueño con empresa
    }

    @Test void onlyTheCompanyAdministratorMayAsk() {
        error(get(OK, user.toString(), "OPERATOR", company), 403, "FORBIDDEN");
        error(get(OK, user.toString(), "LICENSE_OWNER", null), 403, "FORBIDDEN");
        assertThat(get(OK, user.toString(), "COMPANY_ADMIN", company).getStatusCode().value()).isEqualTo(200);
    }

    @Test void aMissingOrBadPeriodIsInvalidPeriodWithAnApiError() {
        for (String query : new String[]{"", "?from=2026-10-01", "?to=2026-10-31", "?from=&to=", "?from=2026-13-01&to=2026-10-31", "?from=2026-10-01&to=mañana",
                "?from=2026-10-31&to=2026-10-01"})
            error(get("/api/analytics/summary" + query, user.toString(), "COMPANY_ADMIN", company), 400, "INVALID_PERIOD");
    }

    @Test void thePeriodCannotExceed366Days() {
        assertThat(get("/api/analytics/summary?from=2025-01-01&to=2026-01-01", user.toString(), "COMPANY_ADMIN", company).getStatusCode().value()).isEqualTo(200);
        JsonNode body = error(get("/api/analytics/summary?from=2025-01-01&to=2026-01-02", user.toString(), "COMPANY_ADMIN", company), 400, "INVALID_PERIOD");
        assertThat(body.path("message").asString()).contains("366");
    }

    @Test void theOldRouteAndOtherMethodsAreAnApiErrorToo() {
        error(get("/api/analytics?from=2026-10-01&to=2026-10-31", user.toString(), "COMPANY_ADMIN", company), 404, "NOT_FOUND");
        var post = org.springframework.web.client.RestClient.builder().defaultStatusHandler(status -> true, (req, res) -> { }).build()
                .post().uri("http://localhost:" + port + "/api/analytics/summary").retrieve().toEntity(String.class);
        error(post, 405, "METHOD_NOT_ALLOWED");
    }

    @Test void theCompanyAlwaysComesFromTheHeaderNeverFromARequestParameter() {
        UUID other = UUID.randomUUID();
        UUID batch = UUID.randomUUID();
        deliver("BatchRegistered", batch, java.util.Map.of("batchId", batch, "companyId", other), java.time.Instant.parse("2026-10-10T10:00:00Z"));
        var response = get(OK + "&companyId=" + other + "&company=" + other, user.toString(), "COMPANY_ADMIN", company);
        assertThat(MAPPER.readTree(response.getBody()).path("quality")).isEmpty();
    }
}
