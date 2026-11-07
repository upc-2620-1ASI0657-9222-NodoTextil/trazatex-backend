package com.nodotextil.trazatex.organizationaccess.integration;

import com.nodotextil.trazatex.organizationaccess.application.port.in.BootstrapOwnerUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.in.BootstrapOwnerUseCase.Outcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR-013 y su ampliación: recuperación del Dueño. Los valores del entorno de la prueba: 4 empresas, 5 usuarios por empresa. */
class OwnerBootstrapIT extends IntegrationTestBase {

    @Autowired
    BootstrapOwnerUseCase bootstrap;
    @Autowired
    ApplicationRunner ownerBootstrapRunner;

    private void emptyWorld() {
        jdbc.sql("TRUNCATE outbox, invitations, users, license, companies CASCADE").update();
    }

    private long ownerInvitations(String status) {
        return count("SELECT count(*) FROM invitations WHERE role = 'LICENSE_OWNER' AND status = ?", status);
    }

    @Test
    void withoutALicenseItCreatesItWithTheEnvironmentLimitsAndInvitesTheOwner() {
        emptyWorld();

        assertThat(bootstrap.run()).isEqualTo(Outcome.LICENSE_CREATED);

        assertThat(jdbc.sql("SELECT max_companies || '/' || max_users_per_company FROM license").query(String.class).single()).isEqualTo("4/5");
        assertThat(jdbc.sql("""
                SELECT email || '|' || display_name || '|' || coalesce(issued_by::text, 'nadie') || '|' || status || '|' || coalesce(company_id::text, 'sin-empresa')
                FROM invitations WHERE role = 'LICENSE_OWNER'
                """).query(String.class).single()).isEqualTo("owner@nodotextil.pe|Rosa Díaz de la Cruz|nadie|PENDING|sin-empresa");
        assertThat(outboxCount("InvitationCreated")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT payload->'payload'->>'fullName' || '|' || (payload->'payload'->>'role') FROM outbox WHERE event_type = 'InvitationCreated'")
                .query(String.class).single()).isEqualTo("Rosa Díaz de la Cruz|LICENSE_OWNER");
        assertThat(count("SELECT count(*) FROM users")).isZero();   // nunca crea cuentas ni contraseñas
    }

    @Test
    void runningItAgainDoesNothing() {
        emptyWorld();
        bootstrap.run();

        assertThat(bootstrap.run()).isEqualTo(Outcome.NOTHING_TO_DO);
        assertThat(bootstrap.run()).isEqualTo(Outcome.NOTHING_TO_DO);

        assertThat(count("SELECT count(*) FROM license")).isEqualTo(1);
        assertThat(ownerInvitations("PENDING")).isEqualTo(1);
        assertThat(outboxCount("InvitationCreated")).isEqualTo(1);
    }

    @Test
    void withAnActiveOwnerItDoesNothingAndKeepsTheExistingLimits() {
        // el mundo de partida: licencia 3/3 y un Dueño activo
        assertThat(bootstrap.run()).isEqualTo(Outcome.NOTHING_TO_DO);

        assertThat(jdbc.sql("SELECT max_companies || '/' || max_users_per_company FROM license").query(String.class).single()).isEqualTo("3/3");
        assertThat(count("SELECT count(*) FROM invitations")).isZero();
        assertThat(count("SELECT count(*) FROM outbox")).isZero();
    }

    @Test
    void withALicenseButNoOwnerAndNoInvitationItIssuesANewOwnerInvitation() {
        jdbc.sql("DELETE FROM users").update();

        assertThat(bootstrap.run()).isEqualTo(Outcome.OWNER_INVITATION_REISSUED);

        assertThat(ownerInvitations("PENDING")).isEqualTo(1);
        assertThat(outboxCount("InvitationCreated")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT max_companies FROM license").query(Integer.class).single()).isEqualTo(3);   // límites intactos
        assertThat(bootstrap.run()).isEqualTo(Outcome.NOTHING_TO_DO);
        assertThat(ownerInvitations("PENDING")).isEqualTo(1);
    }

    @Test
    void aLiveOwnerInvitationIsEnough() {
        jdbc.sql("DELETE FROM users").update();
        insertInvitation(null, OWNER_EMAIL, "LICENSE_OWNER", "PENDING", null, START.minus(Duration.ofDays(3)));

        assertThat(bootstrap.run()).isEqualTo(Outcome.NOTHING_TO_DO);
        assertThat(count("SELECT count(*) FROM invitations")).isEqualTo(1);
    }

    @Test
    void anExpiredOwnerInvitationIsReplacedAndMarkedExpired() {
        jdbc.sql("DELETE FROM users").update();
        insertInvitation(null, OWNER_EMAIL, "LICENSE_OWNER", "PENDING", null, START.minus(Duration.ofDays(8)));   // vencida, aún PENDING
        long eventsBefore = count("SELECT count(*) FROM outbox");

        assertThat(bootstrap.run()).isEqualTo(Outcome.OWNER_INVITATION_REISSUED);

        assertThat(jdbc.sql("SELECT string_agg(status, ',' ORDER BY created_at) FROM invitations WHERE role = 'LICENSE_OWNER'")
                .query(String.class).single()).isEqualTo("EXPIRED,PENDING");
        assertThat(count("SELECT count(*) FROM outbox")).isEqualTo(eventsBefore + 1);
    }

    @Test
    void cancelledOrExpiredOwnerInvitationsDoNotCountAsLive() {
        jdbc.sql("DELETE FROM users").update();
        insertInvitation(null, OWNER_EMAIL, "LICENSE_OWNER", "CANCELLED", null, START.minus(Duration.ofDays(1)));

        assertThat(bootstrap.run()).isEqualTo(Outcome.OWNER_INVITATION_REISSUED);
    }

    @Test
    void ifTheOwnerEmailAlreadyHasAnInactiveAccountItDoesNotCrashAndInvitesNobody() {
        jdbc.sql("UPDATE users SET status = 'INACTIVE'").update();

        assertThat(bootstrap.run()).isEqualTo(Outcome.OWNER_INVITATION_BLOCKED);

        assertThat(count("SELECT count(*) FROM invitations")).isZero();
        assertThat(count("SELECT count(*) FROM outbox")).isZero();
    }

    @Test
    void twoInstancesStartingAtTheSameTimeCreateOneLicenseAndOneInvitation() throws Exception {
        emptyWorld();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Void>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<Void> start = () -> {
                    ready.countDown();
                    go.await();
                    ownerBootstrapRunner.run(null);
                    return null;
                };
                futures.add(pool.submit(start));
            }
            ready.await();
            go.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(count("SELECT count(*) FROM license")).isEqualTo(1);
        assertThat(ownerInvitations("PENDING")).isEqualTo(1);
        assertThat(outboxCount("InvitationCreated")).isEqualTo(1);
    }

    @Test
    void theOwnerAcceptsTheBootstrapInvitationLogsInAndSeesTheLicense() throws Exception {
        emptyWorld();
        bootstrap.run();
        String token = tokenOfEmail(OWNER_EMAIL);

        perform(GET("/api/invitations/" + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LICENSE_OWNER"))
                .andExpect(jsonPath("$.displayName").value("Rosa Díaz de la Cruz"))
                .andExpect(jsonPath("$.companyName").doesNotExist());
        perform(body(POST("/api/invitations/" + token + "/accept"),
                "{\"firstName\":\"Rosa\",\"lastName\":\"Díaz de la Cruz\",\"password\":\"%s\",\"passwordConfirmation\":\"%s\"}".formatted(PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("LICENSE_OWNER"))
                .andExpect(jsonPath("$.companyId").doesNotExist());

        String jwt = json.readTree(perform(body(POST("/api/auth/login"),
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(OWNER_EMAIL, PASSWORD))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("user").get("id").asString();
        UUID owner = UUID.fromString(jwt);
        perform(GET("/api/license").header("X-User-Id", owner.toString()).header("X-User-Role", "LICENSE_OWNER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maxCompanies").value(4)).andExpect(jsonPath("$.maxUsersPerCompany").value(5));
        assertThat(bootstrap.run()).isEqualTo(Outcome.NOTHING_TO_DO);   // ya hay un Dueño activo
    }
}
