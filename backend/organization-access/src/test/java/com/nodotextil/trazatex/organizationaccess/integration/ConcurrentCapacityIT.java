package com.nodotextil.trazatex.organizationaccess.integration;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrencia de cupos (RF-013): la fila de la empresa (usuarios) y la de la licencia (empresas) se bloquean con
 * SELECT ... FOR UPDATE, de modo que dos operaciones simultáneas por el último cupo no pasan ambas.
 */
class ConcurrentCapacityIT extends IntegrationTestBase {

    private static final int ROUNDS = 8;

    private List<Integer> concurrently(List<Callable<Integer>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch ready = new CountDownLatch(calls.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private int status(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return result.andReturn().getResponse().getStatus();
    }

    @RepeatedTest(ROUNDS)
    void twoSimultaneousInvitationsForTheLastSlotCreateOnlyOne() throws Exception {
        jdbc.sql("UPDATE license SET max_users_per_company = 2").update();
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        UUID admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");   // ocupa 1 de 2 cupos

        List<Integer> statuses = concurrently(List.of(
                () -> status(perform(body(asAdmin(POST("/api/invitations/operators"), admin, company),
                        "{\"firstName\":\"Luis\",\"lastName\":\"Rojas\",\"email\":\"luis@hilos.pe\",\"position\":\"Tejedor\"}"))),
                () -> status(perform(body(asAdmin(POST("/api/invitations/operators"), admin, company),
                        "{\"firstName\":\"Mara\",\"lastName\":\"Paz\",\"email\":\"mara@hilos.pe\",\"position\":\"Tejedora\"}")))));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(count("SELECT count(*) FROM invitations WHERE company_id = ?", company)).isEqualTo(1);
        assertThat(outboxCount("InvitationCreated")).isEqualTo(1);
    }

    @RepeatedTest(ROUNDS)
    void manySimultaneousInvitationsNeverExceedTheLimit() throws Exception {
        jdbc.sql("UPDATE license SET max_users_per_company = 4").update();
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        UUID admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");   // 1 de 4

        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String email = "op" + i + "@hilos.pe";
            calls.add(() -> status(perform(body(asAdmin(POST("/api/invitations/operators"), admin, company),
                    "{\"firstName\":\"Op\",\"lastName\":\"Uno\",\"email\":\"" + email + "\",\"position\":\"Tejedor\"}"))));
        }

        List<Integer> statuses = concurrently(calls);

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(3);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM invitations WHERE company_id = ? AND status = 'PENDING'", company)).isEqualTo(3);
    }

    @RepeatedTest(ROUNDS)
    void aSimultaneousReactivationAndInvitationShareTheLastSlotFairly() throws Exception {
        jdbc.sql("UPDATE license SET max_users_per_company = 2").update();
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        UUID admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
        UUID operator = insertUser(company, "Op", "op@hilos.pe", "OPERATOR", "INACTIVE");

        List<Integer> statuses = concurrently(List.of(
                () -> status(perform(body(asAdmin(PATCH("/api/users/" + operator + "/status"), admin, company), "{\"status\":\"ACTIVE\"}"))),
                () -> status(perform(body(asAdmin(POST("/api/invitations/operators"), admin, company),
                        "{\"firstName\":\"Mara\",\"lastName\":\"Paz\",\"email\":\"mara@hilos.pe\",\"position\":\"Tejedora\"}")))));

        // Quien entre primero se queda con el único cupo (la reactivación da 200, la invitación 201); el otro recibe 409
        assertThat(statuses.stream().sorted().toList()).isIn(List.of(200, 409), List.of(201, 409));
        assertThat(count("SELECT count(*) FROM users WHERE company_id = ? AND status = 'ACTIVE'", company)
                + count("SELECT count(*) FROM invitations WHERE company_id = ? AND status = 'PENDING'", company)).isEqualTo(2);
    }

    @RepeatedTest(ROUNDS)
    void twoSimultaneousCompanyCreationsForTheLastCompanySlotCreateOnlyOne() throws Exception {
        insertCompany(VALID_RUC, "Una SAC", "ACTIVE");
        insertCompany(OTHER_RUC, "Dos SAC", "ACTIVE");   // 2 de 3

        List<Integer> statuses = concurrently(List.of(
                () -> status(perform(body(asOwner(POST("/api/companies")),
                        "{\"ruc\":\"" + THIRD_RUC + "\",\"businessName\":\"Tres SAC\",\"activities\":[\"SEWING\"],\"adminEmail\":\"a@tres.pe\"}"))),
                () -> status(perform(body(asOwner(POST("/api/companies")),
                        "{\"ruc\":\"20512528458\",\"businessName\":\"Cuatro SAC\",\"activities\":[\"CUTTING\"],\"adminEmail\":\"a@cuatro.pe\"}")))));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(count("SELECT count(*) FROM companies")).isEqualTo(3);
        assertThat(outboxCount("CompanyRegistered")).isEqualTo(1);
    }

    @RepeatedTest(ROUNDS)
    void twoSimultaneousAcceptancesOfTheSameLinkCreateOneAccount() throws Exception {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        UUID admin = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
        String token = tokenOf(insertInvitation(company, "luis@hilos.pe", "OPERATOR", "PENDING", admin, START));
        String accept = "{\"firstName\":\"Luis\",\"lastName\":\"Rojas\",\"password\":\"contraseña1\",\"passwordConfirmation\":\"contraseña1\"}";

        List<Integer> statuses = concurrently(List.of(
                () -> status(perform(body(POST("/api/invitations/" + token + "/accept"), accept))),
                () -> status(perform(body(POST("/api/invitations/" + token + "/accept"), accept)))));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(count("SELECT count(*) FROM users WHERE email = 'luis@hilos.pe'")).isEqualTo(1);
        assertThat(outboxCount("UserAccountUpdated")).isEqualTo(1);
    }

    @Test
    void twoSimultaneousInactivationsOfTheOnlyTwoAdminsLeaveOneActive() throws Exception {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        UUID first = insertUser(company, "Ana", "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
        UUID second = insertUser(company, "Bea", "bea@hilos.pe", "COMPANY_ADMIN", "ACTIVE");

        List<Integer> statuses = concurrently(List.of(
                () -> status(perform(body(asAdmin(PATCH("/api/users/" + second + "/status"), first, company), "{\"status\":\"INACTIVE\"}"))),
                () -> status(perform(body(asAdmin(PATCH("/api/users/" + first + "/status"), second, company), "{\"status\":\"INACTIVE\"}")))));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(count("SELECT count(*) FROM users WHERE company_id = ? AND role = 'COMPANY_ADMIN' AND status = 'ACTIVE'", company)).isEqualTo(1);
    }
}
