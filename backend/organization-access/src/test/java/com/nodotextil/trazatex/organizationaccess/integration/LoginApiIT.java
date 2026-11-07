package com.nodotextil.trazatex.organizationaccess.integration;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.nodotextil.trazatex.organizationaccess.application.port.in.MaintainLoginAttemptsUseCase;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US05, RNF-06, ADR-005. */
class LoginApiIT extends IntegrationTestBase {

    UUID company;
    UUID admin;

    @BeforeEach
    void users() {
        company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        admin = insertUserWithPassword(company, "ana@hilos.pe", "COMPANY_ADMIN", "ACTIVE");
    }

    private ResultActions login(String email, String password) throws Exception {
        return perform(body(POST("/api/auth/login"), "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private int loginStatus(String email, String password) throws Exception {
        return login(email, password).andReturn().getResponse().getStatus();
    }

    // ---- Éxito y JWT ----

    @Test
    void validCredentialsReturnAnRs256JwtThatTheJwksKeyVerifies() throws Exception {
        String response = login("ana@hilos.pe", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("ana@hilos.pe"))
                .andExpect(jsonPath("$.user.role").value("COMPANY_ADMIN"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = json.readTree(response);

        SignedJWT jwt = SignedJWT.parse(body.get("token").asString());
        String jwks = perform(GET("/.well-known/jwks.json")).andReturn().getResponse().getContentAsString();
        RSAKey key = (RSAKey) JWKSet.parse(jwks).getKeyByKeyId(jwt.getHeader().getKeyID());

        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(key).isNotNull();
        assertThat(jwt.verify(new RSASSAVerifier(key))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(admin.toString());
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("COMPANY_ADMIN");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("companyId")).isEqualTo(company.toString());
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(clock.instant().plus(Duration.ofHours(8)));
        assertThat(body.get("expiresAt").asString()).isEqualTo("2026-10-19T20:00:00Z");
    }

    @Test
    void theOwnerLogsInWithoutACompany() throws Exception {
        UUID owner = insertUserWithPassword(null, "dueno@nodotextil.pe", "LICENSE_OWNER", "ACTIVE");

        String token = json.readTree(login("dueno@nodotextil.pe", PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asString();

        SignedJWT jwt = SignedJWT.parse(token);
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(owner.toString());
        assertThat(jwt.getJWTClaimsSet().getClaims()).doesNotContainKey("companyId");
    }

    @Test
    void emailIsNotCaseSensitive() throws Exception {
        login("ANA@Hilos.PE", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void usersOfAnInactiveCompanyCanLogIn() throws Exception {
        jdbc.sql("UPDATE companies SET status = 'INACTIVE' WHERE id = :id").param("id", company).update();
        login("ana@hilos.pe", PASSWORD).andExpect(status().isOk());
    }

    // ---- Credenciales inválidas: siempre el mismo mensaje ----

    @Test
    void wrongPasswordUnknownEmailInactiveUserAndMalformedEmailAllGetTheSameGenericAnswer() throws Exception {
        insertUserWithPassword(company, "baja@hilos.pe", "OPERATOR", "INACTIVE");
        List<String> bodies = new ArrayList<>();
        for (String[] attempt : new String[][]{
                {"ana@hilos.pe", "incorrecta-1"}, {"nadie@hilos.pe", PASSWORD}, {"baja@hilos.pe", PASSWORD}, {"no-es-un-correo", PASSWORD}}) {
            ResultActions result = login(attempt[0], attempt[1])
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
            JsonNode error = json.readTree(result.andReturn().getResponse().getContentAsString());
            bodies.add(error.get("code").asString() + "|" + error.get("message").asString());
        }
        assertThat(bodies).hasSize(4).containsOnly(bodies.get(0));
    }

    @Test
    void missingEmailOrPasswordIsABadRequest() throws Exception {
        perform(body(POST("/api/auth/login"), "{\"email\":\"ana@hilos.pe\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details", containsInAnyOrder("password")));
    }

    // ---- RNF-06: bloqueo ----

    @Test
    void threeConsecutiveFailuresLockTheAccountForFifteenMinutesEvenForTheCorrectPassword() throws Exception {
        login("ana@hilos.pe", "mala-1").andExpect(status().isUnauthorized());
        login("ana@hilos.pe", "mala-2").andExpect(status().isUnauthorized());
        login("ana@hilos.pe", "mala-3").andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        login("ana@hilos.pe", PASSWORD).andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        login("ana@hilos.pe", PASSWORD).andExpect(status().isLocked());

        clock.advance(Duration.ofSeconds(1));
        login("ana@hilos.pe", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void failedAttemptsArePersistedAndASuccessfulLoginResetsTheCounter() throws Exception {
        login("ana@hilos.pe", "mala-1").andExpect(status().isUnauthorized());
        login("ana@hilos.pe", "mala-2").andExpect(status().isUnauthorized());
        assertThat(attemptsOf("ana@hilos.pe")).isEqualTo(2);

        login("ana@hilos.pe", PASSWORD).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM login_attempts WHERE email = 'ana@hilos.pe'")).as("un login correcto olvida los fallos").isZero();

        login("ana@hilos.pe", "mala-3").andExpect(status().isUnauthorized());
        login("ana@hilos.pe", "mala-4").andExpect(status().isUnauthorized());
        login("ana@hilos.pe", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void afterTheLockExpiresTheUserGetsThreeFreshAttempts() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("ana@hilos.pe", "mala-" + i);
        }
        clock.advance(Duration.ofMinutes(15));

        login("ana@hilos.pe", "mala-a").andExpect(status().isUnauthorized());
        login("ana@hilos.pe", "mala-b").andExpect(status().isUnauthorized());
        login("ana@hilos.pe", "mala-c").andExpect(status().isLocked());
    }

    @Test
    void simultaneousWrongAttemptsCannotSkipTheLimit() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch ready = new CountDownLatch(3);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                int n = i;
                Callable<Integer> attempt = () -> {
                    ready.countDown();
                    go.await();
                    return loginStatus("ana@hilos.pe", "mala-simultanea-" + n);
                };
                futures.add(pool.submit(attempt));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses.stream().sorted().toList()).containsExactly(401, 401, 423);
            assertThat(jdbc.sql("SELECT locked_until IS NOT NULL FROM login_attempts WHERE email = 'ana@hilos.pe'").query(Boolean.class).single()).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void otherEmailsAreUnaffectedByAnotherEmailsLock() throws Exception {
        insertUserWithPassword(company, "otra@hilos.pe", "OPERATOR", "ACTIVE");
        for (int i = 0; i < 3; i++) {
            login("ana@hilos.pe", "mala-" + i);
        }

        login("otra@hilos.pe", PASSWORD).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM login_attempts WHERE email = 'otra@hilos.pe'")).isZero();
    }

    @Test
    void theLoginResponseIsNotIdempotencyScopedAndIgnoresAnIdempotencyKey() throws Exception {
        perform(body(POST("/api/auth/login"), "{\"email\":\"ana@hilos.pe\",\"password\":\"%s\"}".formatted(PASSWORD))
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM idempotency_keys")).isZero();
    }

    private int attemptsOf(String email) {
        return jdbc.sql("SELECT failed_attempts FROM login_attempts WHERE email = :e").param("e", email).query(Integer.class).single();
    }

    // ---- El 423 no revela si la cuenta existe: los intentos se cuentan por correo normalizado ----

    @Test
    void anUnknownEmailIsLockedAfterThreeFailuresExactlyLikeAnExistingOne() throws Exception {
        List<Reply> existing = new ArrayList<>();
        List<Reply> unknown = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            existing.add(reply(login("ana@hilos.pe", "mala-" + i)));
            unknown.add(reply(login("nadie@hilos.pe", "mala-" + i)));
        }

        assertThat(unknown).isEqualTo(existing);
        assertThat(unknown.stream().map(Reply::status).toList()).containsExactly(401, 401, 423, 423, 423);
        assertThat(unknown.get(2).code()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(count("SELECT count(*) FROM users WHERE email = 'nadie@hilos.pe'")).as("no se crea ninguna cuenta").isZero();
        assertThat(count("SELECT count(*) FROM login_attempts WHERE email = 'nadie@hilos.pe' AND locked_until IS NOT NULL")).isEqualTo(1);
    }

    private record Reply(int status, String code, String message) {
    }

    private Reply reply(ResultActions result) throws Exception {
        var response = result.andReturn().getResponse();
        JsonNode body = json.readTree(response.getContentAsString());
        return new Reply(response.getStatus(), body.get("code").asString(), body.get("message").asString());
    }

    @Test
    void anUnknownEmailAlsoUnlocksAfterFifteenMinutesAndGetsThreeFreshAttempts() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("nadie@hilos.pe", "mala-" + i);
        }
        login("nadie@hilos.pe", "mala-x").andExpect(status().isLocked());

        clock.advance(Duration.ofMinutes(15));

        login("nadie@hilos.pe", "mala-a").andExpect(status().isUnauthorized());
        login("nadie@hilos.pe", "mala-b").andExpect(status().isUnauthorized());
        login("nadie@hilos.pe", "mala-c").andExpect(status().isLocked());
    }

    @Test
    void attemptsAreCountedPerNormalizedEmailWhateverTheCase() throws Exception {
        login("Nadie@Hilos.PE", "mala-1").andExpect(status().isUnauthorized());
        login(" nadie@hilos.pe ", "mala-2").andExpect(status().isUnauthorized());
        login("NADIE@HILOS.PE", "mala-3").andExpect(status().isLocked());

        assertThat(jdbc.sql("SELECT string_agg(email, ',') FROM login_attempts").query(String.class).single()).isEqualTo("nadie@hilos.pe");
    }

    @Test
    void aLockedUnknownEmailCannotBeToldApartByTheCorrectPasswordEither() throws Exception {
        // la contraseña correcta de una cuenta que existe y la de una que no existe dan la misma respuesta mientras dura el bloqueo
        for (int i = 0; i < 3; i++) {
            login("ana@hilos.pe", "mala-" + i);
            login("nadie@hilos.pe", "mala-" + i);
        }

        Reply real = reply(login("ana@hilos.pe", PASSWORD));
        Reply fake = reply(login("nadie@hilos.pe", PASSWORD));

        assertThat(fake).isEqualTo(real);
        assertThat(real.status()).isEqualTo(423);
    }

    @Test
    void anInactiveAccountLocksLikeAnyOtherEmailAndKeepsTheSameAnswers() throws Exception {
        insertUserWithPassword(company, "baja@hilos.pe", "OPERATOR", "INACTIVE");
        List<Reply> replies = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            replies.add(reply(login("baja@hilos.pe", PASSWORD)));
        }
        assertThat(replies.stream().map(Reply::status).toList()).containsExactly(401, 401, 423, 423);
    }

    @Test
    void malformedEmailsAreNeverRecorded() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("esto-no-es-un-correo", "x").andExpect(status().isUnauthorized());
        }
        assertThat(count("SELECT count(*) FROM login_attempts")).isZero();
    }

    @Test
    void simultaneousWrongAttemptsForAnUnknownEmailCannotSkipTheLimitEither() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch ready = new CountDownLatch(3);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                int n = i;
                Callable<Integer> attempt = () -> {
                    ready.countDown();
                    go.await();
                    return loginStatus("nadie@hilos.pe", "mala-simultanea-" + n);
                };
                futures.add(pool.submit(attempt));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses.stream().sorted().toList()).containsExactly(401, 401, 423);
            assertThat(count("SELECT count(*) FROM login_attempts")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- Limpieza de lo vencido ----

    @Autowired
    MaintainLoginAttemptsUseCase maintenance;

    @Test
    void theCleanupJobRemovesFinishedLocksAndStaleCountersButKeepsWhatIsStillInForce() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("bloqueado-vigente@hilos.pe", "x");          // bloqueado hasta +15 min
            login("bloqueado-vencido@hilos.pe", "x");          // se vence al avanzar el reloj
        }
        login("parcial-reciente@hilos.pe", "x");
        login("parcial-antiguo@hilos.pe", "x");
        assertThat(count("SELECT count(*) FROM login_attempts")).isEqualTo(4);
        // parcial-antiguo: su último fallo fue hace 25 h; bloqueado-vencido: el bloqueo terminó hace 1 min
        jdbc.sql("UPDATE login_attempts SET updated_at = :t WHERE email = 'parcial-antiguo@hilos.pe'").param("t", utc(clock.instant().minus(Duration.ofHours(25)))).update();
        jdbc.sql("UPDATE login_attempts SET locked_until = :t WHERE email = 'bloqueado-vencido@hilos.pe'").param("t", utc(clock.instant().minus(Duration.ofMinutes(1)))).update();

        assertThat(maintenance.purgeExpired()).isEqualTo(2);

        assertThat(jdbc.sql("SELECT string_agg(email, ',' ORDER BY email) FROM login_attempts").query(String.class).single())
                .isEqualTo("bloqueado-vigente@hilos.pe,parcial-reciente@hilos.pe");
        assertThat(maintenance.purgeExpired()).isZero();
        login("bloqueado-vigente@hilos.pe", "x").andExpect(status().isLocked());
    }

    @Test
    void afterTheCleanupAnExpiredLockHasNoEffectOnTheNextAttempts() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("nadie@hilos.pe", "x");
        }
        clock.advance(Duration.ofMinutes(16));

        assertThat(maintenance.purgeExpired()).isEqualTo(1);

        login("nadie@hilos.pe", "x").andExpect(status().isUnauthorized());
        assertThat(attemptsOf("nadie@hilos.pe")).isEqualTo(1);
    }
}
