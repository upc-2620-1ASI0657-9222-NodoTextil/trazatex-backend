package com.nodotextil.trazatex.quality.integration;

import com.nodotextil.trazatex.sharedkernel.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;

/**
 * Base de las pruebas de integración: Spring completo + PostgreSQL real (Testcontainers, una sola instancia para toda la corrida) y
 * reloj controlable. Cada prueba parte de una base vacía. Sin broker por defecto ({@code trazatex.messaging.enabled=false}); las
 * pruebas con Kafka real lo encienden.
 */
@SpringBootTest(properties = {"trazatex.messaging.enabled=false", "trazatex.quality.start.job-enabled=false"})
@AutoConfigureMockMvc
@Import(IntegrationTestBase.TestBeans.class)
abstract class IntegrationTestBase {

    static final Instant START = Instant.parse("2026-10-23T12:00:00Z");
    static final PostgreSQLContainer POSTGRES = SharedPostgres.CONTAINER;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("trazatex.production.base-url", ProductionStub::baseUrl);
        registry.add("trazatex.idempotency.cleanup-job-delay", () -> "PT24H");
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }

        /** Cuenta los SELECT que llegan a la base: las pruebas de paginación comprueban que no hay una consulta por fila (N+1). */
        @Bean
        static StatementCounter statementCounter() {
            return new StatementCounter();
        }
    }

    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetDatabase() {
        jdbc.sql("TRUNCATE evidence_upload_queue, evidence, quality_failure, impacted_batch, outbox, quality_test, quality_control, batch_projection, processed_events, idempotency_keys, audit_log").update();
        ProductionStub.reset();
        clock.advance(Duration.between(clock.instant(), START));
    }
}
