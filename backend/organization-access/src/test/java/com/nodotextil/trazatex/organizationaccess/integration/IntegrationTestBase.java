package com.nodotextil.trazatex.organizationaccess.integration;

import com.nodotextil.trazatex.organizationaccess.integration.FakeExternalServices.FakePasswordBreachChecker;
import com.nodotextil.trazatex.organizationaccess.integration.FakeExternalServices.FakeRucValidator;
import com.nodotextil.trazatex.organizationaccess.TestKeys;
import com.nodotextil.trazatex.sharedkernel.testsupport.MutableClock;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaRucValidatorAdapter;
import com.nodotextil.trazatex.organizationaccess.infrastructure.external.HibpPasswordBreachCheckerAdapter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base de las pruebas de integración: Spring completo + PostgreSQL real (Testcontainers, una sola instancia para toda la
 * corrida), reloj controlable y dobles de Chequea.pe y HIBP. Cada prueba parte de una base vacía con una licencia
 * (3 empresas, 3 usuarios por empresa) y un Dueño activo.
 */
/** Sin broker por defecto: el relay y los tópicos se apagan; {@code KafkaPublicationIT} los enciende. */
@SpringBootTest(properties = "trazatex.messaging.enabled=false")
@AutoConfigureMockMvc
@Import(IntegrationTestBase.TestBeans.class)
abstract class IntegrationTestBase {

    static final Instant START = Instant.parse("2026-10-19T12:00:00Z");
    static final String OWNER_EMAIL = "owner@nodotextil.pe";
    static final String TEST_CHEQUEA_TOKEN = "token-chequea-de-prueba";
    static final String PASSWORD = "contraseña-segura-1";
    private static final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder ENCODER =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15");

    /** Chequea.pe y HIBP simulados: las pruebas nunca llaman a las APIs reales. */
    static final WireMockServer WIREMOCK = new WireMockServer(com.github.tomakehurst.wiremock.core.WireMockConfiguration.options().dynamicPort());

    static {
        POSTGRES.start();
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("trazatex.chequea.base-url", WIREMOCK::baseUrl);
        registry.add("trazatex.chequea.token", () -> TEST_CHEQUEA_TOKEN);
        registry.add("trazatex.chequea.timeout", () -> "PT1S");
        registry.add("trazatex.hibp.base-url", WIREMOCK::baseUrl);
        registry.add("trazatex.hibp.timeout", () -> "PT1S");
        registry.add("trazatex.jwt.private-key", TestKeys::defaultPrivateKey);
        registry.add("trazatex.jwt.public-key", TestKeys::defaultPublicKey);
        registry.add("trazatex.jwt.key-id", () -> TestKeys.KEY_ID);
        registry.add("trazatex.bootstrap.owner-email", () -> OWNER_EMAIL);
        registry.add("trazatex.bootstrap.owner-name", () -> "Rosa Díaz de la Cruz");
        registry.add("trazatex.bootstrap.max-companies", () -> "4");
        registry.add("trazatex.bootstrap.max-users-per-company", () -> "5");
        registry.add("trazatex.invitations.expiration-job-delay", () -> "PT24H");
        registry.add("trazatex.idempotency.cleanup-job-delay", () -> "PT24H");
        registry.add("trazatex.login.cleanup-job-delay", () -> "PT24H");
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }

        @Bean
        @Primary
        FakeRucValidator fakeRucValidator() {
            return new FakeRucValidator();
        }

        @Bean
        @Primary
        FakePasswordBreachChecker fakePasswordBreachChecker() {
            return new FakePasswordBreachChecker();
        }
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected FakeRucValidator rucValidator;
    @Autowired
    protected FakePasswordBreachChecker breachChecker;
    @Autowired
    protected ChequeaRucValidatorAdapter chequeaAdapter;
    @Autowired
    protected HibpPasswordBreachCheckerAdapter hibpAdapter;
    @Autowired
    protected CircuitBreakerRegistry circuitBreakers;

    protected UUID ownerId;

    @BeforeEach
    void resetDatabaseAndWorld() {
        jdbc.sql("""
                TRUNCATE outbox, idempotency_keys, audit_log, login_attempts, privacy_settings, admin_requests, invitations, users,
                         company_activities, companies, license, validated_ruc_cache
                """).update();
        rucValidator.reset();
        breachChecker.reset();
        WIREMOCK.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        setClock(START);
        insertLicense(3, 3);
        ownerId = insertUser(null, "OWNER", OWNER_EMAIL, "LICENSE_OWNER", "ACTIVE");
    }

    /** La prueba usa los adaptadores reales de Chequea.pe y HIBP, contra WireMock, en lugar de los dobles. */
    protected void useRealExternalServices() {
        rucValidator.delegate = chequeaAdapter;
        breachChecker.delegate = hibpAdapter;
    }

    // ---- Datos de partida (directo en la base) ----

    protected void setClock(Instant instant) {
        clock.advance(java.time.Duration.between(clock.instant(), instant));
    }

    protected void insertLicense(int maxCompanies, int maxUsersPerCompany) {
        jdbc.sql("INSERT INTO license (id, max_companies, max_users_per_company, created_at) VALUES (:id, :c, :u, :at)")
                .param("id", UUID.randomUUID()).param("c", maxCompanies).param("u", maxUsersPerCompany)
                .param("at", utc(START)).update();
    }

    protected UUID insertCompany(String ruc, String name, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO companies (id, ruc, business_name, status, created_at) VALUES (:id, :ruc, :name, :status, :at)")
                .param("id", id).param("ruc", ruc).param("name", name).param("status", status).param("at", utc(START)).update();
        jdbc.sql("INSERT INTO company_activities (company_id, activity) VALUES (:id, 'WEAVING')").param("id", id).update();
        return id;
    }

    protected UUID insertUser(UUID companyId, String firstName, String email, String role, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, company_id, email, first_name, last_name, password_hash, role, status, created_at)
                        VALUES (:id, :company, :email, :first, 'Prueba', 'hash', :role, :status, :at)
                        """)
                .param("id", id).param("company", companyId).param("email", email).param("first", firstName)
                .param("role", role).param("status", status).param("at", utc(START)).update();
        return id;
    }

    /** Usuario cuya contraseña es {@link #PASSWORD} (hash BCrypt real), para probar el login. */
    protected UUID insertUserWithPassword(UUID companyId, String email, String role, String status) {
        UUID id = insertUser(companyId, "Ana", email, role, status);
        jdbc.sql("UPDATE users SET password_hash = :hash WHERE id = :id").param("hash", ENCODER.encode(PASSWORD)).param("id", id).update();
        return id;
    }

    protected UUID insertInvitation(UUID companyId, String email, String role, String status, UUID issuedBy, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO invitations (id, token, email, display_name, first_name, last_name, position, role, company_id, issued_by, status, created_at, expires_at)
                        VALUES (:id, :token, :email, 'Luis Rojas', 'Luis', 'Rojas', 'Tejedor', :role, :company, :issuedBy, :status, :createdAt, :expiresAt)
                        """)
                .param("id", id).param("token", UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""))
                .param("email", email).param("role", role).param("company", companyId).param("issuedBy", issuedBy)
                .param("status", status).param("createdAt", utc(createdAt)).param("expiresAt", utc(createdAt.plus(java.time.Duration.ofDays(7))))
                .update();
        return id;
    }

    protected String tokenOf(UUID invitationId) {
        return jdbc.sql("SELECT token FROM invitations WHERE id = :id").param("id", invitationId).query(String.class).single();
    }

    protected String tokenOfEmail(String email) {
        return jdbc.sql("SELECT token FROM invitations WHERE email = :e AND status = 'PENDING'").param("e", email).query(String.class).single();
    }

    protected long count(String sql, Object... params) {
        var statement = jdbc.sql(sql);
        for (Object param : params) {
            statement = statement.param(param);
        }
        return statement.query(Long.class).single();
    }

    protected static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    // ---- Peticiones ----

    protected static final String VALID_RUC = "20100070970";
    protected static final String OTHER_RUC = "20131312955";
    protected static final String THIRD_RUC = "20100047218";

    protected MockHttpServletRequestBuilder asOwner(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", ownerId.toString()).header("X-User-Role", "LICENSE_OWNER");
    }

    protected MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request, UUID userId, UUID companyId) {
        return as(request, userId, companyId, "COMPANY_ADMIN");
    }

    protected MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID userId, UUID companyId, String role) {
        MockHttpServletRequestBuilder builder = request.header("X-User-Id", userId.toString()).header("X-User-Role", role);
        return companyId == null ? builder : builder.header("X-Company-Id", companyId.toString());
    }

    protected static MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static MockHttpServletRequestBuilder POST(String url) {
        return post(url);
    }

    protected static MockHttpServletRequestBuilder GET(String url) {
        return get(url);
    }

    protected static MockHttpServletRequestBuilder PATCH(String url) {
        return patch(url);
    }

    protected static MockHttpServletRequestBuilder PUT(String url) {
        return put(url);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected long outboxCount(String eventType) {
        return count("SELECT count(*) FROM outbox WHERE event_type = ?", eventType);
    }
}
