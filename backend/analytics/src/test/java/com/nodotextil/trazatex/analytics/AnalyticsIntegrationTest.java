package com.nodotextil.trazatex.analytics;

import com.nodotextil.trazatex.analytics.application.AnalyticsProjectionService;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Base de las pruebas con PostgreSQL real y el servicio levantado (sin Kafka): los eventos entran por {@link AnalyticsProjectionService}, que es lo
 * que ejecuta el listener, y las consultas salen por HTTP como las haría el Gateway. Un solo contenedor para todas las pruebas de la JVM.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "trazatex.messaging.enabled=false")
public abstract class AnalyticsIntegrationTest {
    public static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString());
    public static final tools.jackson.databind.ObjectMapper MAPPER = JsonMapper.builder().build();
    static { DB.start(); }

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired protected AnalyticsProjectionService projection;
    @Autowired protected JdbcClient jdbc;
    @LocalServerPort protected int port;

    @BeforeEach void emptyTheReadModel() { jdbc.sql("TRUNCATE analytics_event,batch_birth,batch_owner,batch_quality").update(); }

    /** El sobre de un evento tal como Kafka lo entrega. */
    public static String envelope(String type, Object aggregate, Object payload, Instant at) {
        return MAPPER.writeValueAsString(BaseDomainEvent.of(type, aggregate.toString(), payload, Clock.fixed(at, ZoneOffset.UTC)));
    }

    /** Entrega un evento al proyector y devuelve su sobre (para poder repetirlo). */
    protected String deliver(String type, Object aggregate, Object payload, Instant at) {
        String json = envelope(type, aggregate, payload, at);
        projection.consume(json);
        return json;
    }

    protected ResponseEntity<String> get(String pathAndQuery, String userId, String role, UUID company) {
        var request = RestClient.builder().defaultStatusHandler(status -> true, (req, res) -> { }).build()
                .get().uri("http://localhost:" + port + pathAndQuery);
        if (userId != null) request.header("X-User-Id", userId);
        if (role != null) request.header("X-User-Role", role);
        if (company != null) request.header("X-Company-Id", company.toString());
        return request.retrieve().toEntity(String.class);
    }

    protected JsonNode summary(UUID company, String from, String to) {
        var response = get("/api/analytics/summary?from=" + from + "&to=" + to, UUID.randomUUID().toString(), "COMPANY_ADMIN", company);
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return MAPPER.readTree(response.getBody());
    }
}
