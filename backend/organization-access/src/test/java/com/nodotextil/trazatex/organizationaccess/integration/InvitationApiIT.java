package com.nodotextil.trazatex.organizationaccess.integration;

import com.nodotextil.trazatex.organizationaccess.application.port.in.ManageInvitationsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US04, US06, US41 y la vigencia de las invitaciones. */
class InvitationApiIT extends IntegrationTestBase {

    @Autowired
    ManageInvitationsUseCase invitationsUseCase;

    UUID company;
    UUID admin;

    @BeforeEach
    void company() {
        company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
    }

    private static String operatorBody(String email) {
        return """
                {"firstName":"Luis","lastName":"Rojas","email":"%s","position":"Tejedor"}
                """.formatted(email);
    }

    private static String acceptBody(String password, String confirmation) {
        return """
                {"firstName":"Luis","lastName":"Rojas Díaz","password":"%s","passwordConfirmation":"%s"}
                """.formatted(password, confirmation);
    }

    private org.springframework.test.web.servlet.ResultActions invite(String email) throws Exception {
        return perform(body(asAdmin(POST("/api/invitations/operators"), admin, company), operatorBody(email)));
    }

    private org.springframework.test.web.servlet.ResultActions accept(String token, String password, String confirmation) throws Exception {
        return perform(body(POST("/api/invitations/" + token + "/accept"), acceptBody(password, confirmation)));
    }

    // ---- US06: invitar operario ----

    @Test
    void adminInvitesAnOperator() throws Exception {
        invite("luis@hilos.pe")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.companyId").value(company.toString()))
                .andExpect(jsonPath("$.issuedBy").value(admin.toString()))
                .andExpect(jsonPath("$", not(hasKey("token"))));

        assertThat(jdbc.sql("SELECT expires_at - created_at FROM invitations WHERE email = 'luis@hilos.pe'")
                .query(String.class).single()).startsWith("7 days");
        assertThat(outboxCount("InvitationCreated")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT payload->'payload'->>'fullName' FROM outbox WHERE event_type = 'InvitationCreated'")
                .query(String.class).single()).isEqualTo("Luis Rojas");
        assertThat(jdbc.sql("SELECT topic FROM outbox WHERE event_type = 'InvitationCreated'").query(String.class).single())
                .isEqualTo("orgaccess.invitation");
    }

    @Test
    void theLastSlotIsTakenAndTheNextInvitationIsRejectedWithoutSideEffects() throws Exception {
        invite("uno@hilos.pe").andExpect(status().isCreated());
        invite("dos@hilos.pe").andExpect(status().isCreated());

        invite("tres@hilos.pe")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_LIMIT_REACHED"));

        assertThat(count("SELECT count(*) FROM invitations WHERE company_id = ?", company)).isEqualTo(2);
        assertThat(outboxCount("InvitationCreated")).isEqualTo(2);
    }

    @Test
    void inactiveUsersAndCancelledInvitationsDoNotTakeASlot() throws Exception {
        insertUser(company, "Baja", "baja@hilos.pe", "OPERATOR", "INACTIVE");
        insertInvitation(company, "cancelada@hilos.pe", "OPERATOR", "CANCELLED", admin, START);

        invite("uno@hilos.pe").andExpect(status().isCreated());
        invite("dos@hilos.pe").andExpect(status().isCreated());
    }

    @Test
    void anEmailThatAlreadyHasAnAccountOrAPendingInvitationIsRejected() throws Exception {
        insertUser(company, "Luis", "luis@hilos.pe", "OPERATOR", "ACTIVE");
        invite("LUIS@hilos.pe").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        invite("nuevo@hilos.pe").andExpect(status().isCreated());
        invite("Nuevo@Hilos.pe").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_ALREADY_PENDING"));
    }

    @Test
    void operatorDataIsRequired() throws Exception {
        perform(body(asAdmin(POST("/api/invitations/operators"), admin, company), "{\"email\":\"luis@hilos.pe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", containsInAnyOrder("firstName", "lastName", "position")));
    }

    @Test
    void onlyCompanyAdminsInviteOperators() throws Exception {
        perform(body(asOwner(POST("/api/invitations/operators")), operatorBody("luis@hilos.pe"))).andExpect(status().isForbidden());
        UUID operator = insertUser(company, "Op", "op@hilos.pe", "OPERATOR", "ACTIVE");
        perform(body(as(POST("/api/invitations/operators"), operator, company, "OPERATOR"), operatorBody("luis@hilos.pe")))
                .andExpect(status().isForbidden());
    }

    // ---- US41: cancelar ----

    @Test
    void theIssuerCancelsAPendingInvitation() throws Exception {
        UUID id = insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START);

        perform(asAdmin(POST("/api/invitations/" + id + "/cancel"), admin, company))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(jdbc.sql("SELECT status FROM invitations WHERE id = :id").param("id", id).query(String.class).single()).isEqualTo("CANCELLED");
        accept(tokenOf(id), "contraseña1", "contraseña1").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_CANCELLED"));
    }

    @Test
    void nobodyButTheIssuerCancels() throws Exception {
        UUID otherAdmin = insertUser(company, "Otra", "otra@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
        UUID id = insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START);

        perform(asAdmin(POST("/api/invitations/" + id + "/cancel"), otherAdmin, company))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVITATION_NOT_ISSUER"));
        assertThat(jdbc.sql("SELECT status FROM invitations WHERE id = :id").param("id", id).query(String.class).single()).isEqualTo("PENDING");
    }

    @Test
    void acceptedCancelledOrExpiredInvitationsCannotBeCancelledAndKeepTheirState() throws Exception {
        UUID accepted = insertInvitation(company, "a@hilos.pe", "OPERATOR", "ACCEPTED", admin, START);
        UUID cancelled = insertInvitation(company, "b@hilos.pe", "OPERATOR", "CANCELLED", admin, START);
        UUID expired = insertInvitation(company, "c@hilos.pe", "OPERATOR", "PENDING", admin, START.minus(Duration.ofDays(8)));

        for (UUID id : new UUID[]{accepted, cancelled, expired}) {
            perform(asAdmin(POST("/api/invitations/" + id + "/cancel"), admin, company))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));
        }
        assertThat(jdbc.sql("SELECT status FROM invitations WHERE id = :id").param("id", accepted).query(String.class).single()).isEqualTo("ACCEPTED");
        assertThat(jdbc.sql("SELECT status FROM invitations WHERE id = :id").param("id", cancelled).query(String.class).single()).isEqualTo("CANCELLED");
        assertThat(jdbc.sql("SELECT status FROM invitations WHERE id = :id").param("id", expired).query(String.class).single()).isEqualTo("PENDING");
    }

    // ---- Ver el enlace (público) ----

    @Test
    void anyoneWithTheLinkSeesTheInvitationWithoutIdentityHeaders() throws Exception {
        UUID id = insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START);

        perform(GET("/api/invitations/" + tokenOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("luis@hilos.pe"))
                .andExpect(jsonPath("$.companyName").value("Hilos SAC"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        perform(GET("/api/invitations/" + "x".repeat(43))).andExpect(status().isNotFound());
    }

    @Test
    void theLinkReportsExpiredOnceTheSevenDaysPassEvenIfTheJobHasNotRun() throws Exception {
        UUID id = insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START);
        clock.advance(Duration.ofDays(7));

        perform(GET("/api/invitations/" + tokenOf(id))).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    // ---- US04: aceptar ----

    @Test
    void acceptingCreatesTheActiveAccountWithTheInvitationCompanyAndRole() throws Exception {
        UUID id = insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START);

        accept(tokenOf(id), "contraseña1", "contraseña1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("luis@hilos.pe"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.companyId").value(company.toString()))
                .andExpect(jsonPath("$.lastName").value("Rojas Díaz"))
                .andExpect(jsonPath("$", not(hasKey("passwordHash"))));

        assertThat(jdbc.sql("SELECT status FROM invitations WHERE id = :id").param("id", id).query(String.class).single()).isEqualTo("ACCEPTED");
        assertThat(jdbc.sql("SELECT password_hash FROM users WHERE email = 'luis@hilos.pe'").query(String.class).single())
                .startsWith("$2").doesNotContain("contraseña1");
        assertThat(outboxCount("UserAccountUpdated")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT topic FROM outbox WHERE event_type = 'UserAccountUpdated'").query(String.class).single()).isEqualTo("orgaccess.user");
        assertThat(count("SELECT count(*) FROM audit_log")).isZero();
    }

    @Test
    void anInvitationAcceptedOnceCannotCreateAnotherAccount() throws Exception {
        UUID id = insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START);
        accept(tokenOf(id), "contraseña1", "contraseña1").andExpect(status().isCreated());

        accept(tokenOf(id), "otra-contraseña", "otra-contraseña")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_ALREADY_ACCEPTED"));
        assertThat(count("SELECT count(*) FROM users WHERE email = 'luis@hilos.pe'")).isEqualTo(1);
    }

    @Test
    void anExpiredInvitationIsNotAcceptedAndThePendingRowIsLeftForTheJob() throws Exception {
        UUID id = insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START);
        clock.advance(Duration.ofDays(7));

        accept(tokenOf(id), "contraseña1", "contraseña1")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));

        assertThat(count("SELECT count(*) FROM users WHERE email = 'luis@hilos.pe'")).isZero();
        assertThat(jdbc.sql("SELECT status FROM invitations WHERE id = :id").param("id", id).query(String.class).single()).isEqualTo("PENDING");
    }

    @Test
    void aCompromisedPasswordIsRejectedAndTheInvitationStaysUsable() throws Exception {
        UUID id = insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START);
        breachChecker.compromised.add("password123");

        accept(tokenOf(id), "password123", "password123")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_COMPROMISED"));

        assertThat(count("SELECT count(*) FROM users WHERE email = 'luis@hilos.pe'")).isZero();
        accept(tokenOf(id), "contraseña-segura", "contraseña-segura").andExpect(status().isCreated());
    }

    @Test
    void ifTheLeakedPasswordServiceIsDownTheAccountIsCreatedAndTheSkipIsAudited() throws Exception {
        UUID id = insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START);
        breachChecker.unavailable.set(true);

        accept(tokenOf(id), "contraseña1", "contraseña1").andExpect(status().isCreated());

        assertThat(jdbc.sql("SELECT action FROM audit_log").query(String.class).single()).isEqualTo("PASSWORD_BREACH_CHECK_SKIPPED");
    }

    @Test
    void passwordPolicyIsEnforced() throws Exception {
        UUID id = insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START);

        accept(tokenOf(id), "corta", "corta").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
        accept(tokenOf(id), "contraseña1", "contraseña2").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        perform(body(POST("/api/invitations/" + tokenOf(id) + "/accept"), "{\"password\":\"contraseña1\",\"passwordConfirmation\":\"contraseña1\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details", containsInAnyOrder("firstName", "lastName")));
        assertThat(count("SELECT count(*) FROM users WHERE email = 'luis@hilos.pe'")).isZero();
    }

    @Test
    void anUnknownTokenIs404() throws Exception {
        accept("x".repeat(43), "contraseña1", "contraseña1").andExpect(status().isNotFound());
    }

    @Test
    void theIdempotencyKeyIsIgnoredOnThePublicAcceptEndpointInsteadOfFailingWithMissingUserId() throws Exception {
        UUID id = insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START);
        String key = UUID.randomUUID().toString();

        perform(body(POST("/api/invitations/" + tokenOf(id) + "/accept"), acceptBody("contraseña1", "contraseña1"))
                .header("Idempotency-Key", key))
                .andExpect(status().isCreated());

        assertThat(count("SELECT count(*) FROM idempotency_keys")).isZero();
        // no se repite la respuesta guardada: la segunda aceptación se evalúa y la invitación ya fue usada
        perform(body(POST("/api/invitations/" + tokenOf(id) + "/accept"), acceptBody("contraseña1", "contraseña1"))
                .header("Idempotency-Key", key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_ALREADY_ACCEPTED"));
        assertThat(count("SELECT count(*) FROM users WHERE email = 'luis@hilos.pe'")).isEqualTo(1);
    }

    // ---- Vigencia y cupo ----

    @Test
    void anExpiredInvitationNoLongerTakesASlotEvenBeforeTheJobMarksIt() throws Exception {
        invite("uno@hilos.pe").andExpect(status().isCreated());
        invite("dos@hilos.pe").andExpect(status().isCreated());
        invite("tres@hilos.pe").andExpect(status().isConflict());

        clock.advance(Duration.ofDays(7));

        invite("tres@hilos.pe").andExpect(status().isCreated());
        invite("cuatro@hilos.pe").andExpect(status().isCreated());
        invite("cinco@hilos.pe").andExpect(status().isConflict());
        // las dos primeras siguen guardadas como PENDING: el job aún no corrió
        assertThat(count("SELECT count(*) FROM invitations WHERE status = 'PENDING' AND expires_at <= ?", utc(clock.instant()))).isEqualTo(2);
    }

    @Test
    void anExpiredPendingInvitationDoesNotBlockInvitingTheSameEmailAgain() throws Exception {
        invite("luis@hilos.pe").andExpect(status().isCreated());
        invite("luis@hilos.pe").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_ALREADY_PENDING"));

        clock.advance(Duration.ofDays(8));
        invite("luis@hilos.pe").andExpect(status().isCreated());

        assertThat(jdbc.sql("SELECT string_agg(status, ',' ORDER BY created_at) FROM invitations WHERE email = 'luis@hilos.pe'")
                .query(String.class).single()).isEqualTo("EXPIRED,PENDING");
    }

    @Test
    void theExpirationJobMarksOnlyTheDueInvitations() {
        UUID due = insertInvitation(company, "vencida@hilos.pe", "OPERATOR", "PENDING", admin, START.minus(Duration.ofDays(8)));
        UUID live = insertInvitation(company, "vigente@hilos.pe", "OPERATOR", "PENDING", admin, START.minus(Duration.ofDays(6)));
        UUID cancelled = insertInvitation(company, "cancelada@hilos.pe", "OPERATOR", "CANCELLED", admin, START.minus(Duration.ofDays(9)));

        assertThat(invitationsUseCase.expireDueInvitations()).isEqualTo(1);
        assertThat(invitationsUseCase.expireDueInvitations()).isZero();

        assertThat(statusOf(due)).isEqualTo("EXPIRED");
        assertThat(statusOf(live)).isEqualTo("PENDING");
        assertThat(statusOf(cancelled)).isEqualTo("CANCELLED");
    }

    @Test
    void usersOfAnInactiveCompanyCanReadButNotWrite() throws Exception {
        UUID id = insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START);
        jdbc.sql("UPDATE companies SET status = 'INACTIVE' WHERE id = :id").param("id", company).update();

        invite("nuevo@hilos.pe").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"));
        perform(asAdmin(POST("/api/invitations/" + id + "/cancel"), admin, company))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMPANY_INACTIVE"));
        perform(asAdmin(GET("/api/users"), admin, company)).andExpect(status().isOk());
    }

    private String statusOf(UUID invitationId) {
        return jdbc.sql("SELECT status FROM invitations WHERE id = :id").param("id", invitationId).query(String.class).single();
    }
}
