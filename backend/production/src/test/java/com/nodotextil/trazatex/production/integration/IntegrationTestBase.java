package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchFactory;
import com.nodotextil.trazatex.production.domain.model.Composition;
import com.nodotextil.trazatex.production.domain.model.CompanyProjection;
import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.service.TransformationFactory;
import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.CompanyProjectionRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.MachineRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.TransferRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.TransformationRepositoryPort;
import com.nodotextil.trazatex.sharedkernel.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.OPERATOR;
import static com.nodotextil.trazatex.production.domain.Fixtures.composition;

/**
 * Base de las pruebas de integración: Spring completo + PostgreSQL real (Testcontainers, una sola instancia para toda la
 * corrida) y reloj controlable. Cada prueba parte de una base vacía. Sin broker por defecto (`trazatex.messaging.enabled=false`); las pruebas con Kafka real lo encienden.
 */
// CLOUDINARY_URL vacía a propósito: aunque el entorno de quien corre las pruebas la tenga definida, nada sube a un Cloudinary real
@SpringBootTest(properties = {"trazatex.messaging.enabled=false", "trazatex.cloudinary.url=", "trazatex.evidence.upload.enabled=false"})
@AutoConfigureMockMvc
@Import(IntegrationTestBase.TestBeans.class)
abstract class IntegrationTestBase {

    static final Instant START = Instant.parse("2026-10-20T12:00:00Z");
    static final UUID OTHER_COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    static final PostgreSQLContainer POSTGRES = SharedPostgres.CONTAINER;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("trazatex.idempotency.cleanup-job-delay", () -> "PT24H");
        // Captura las sentencias SQL reales que genera Hibernate (para comprobarlas con EXPLAIN)
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector", CapturingStatementInspector.class::getName);
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected PlatformTransactionManager transactionManager;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected BatchFactory batchFactory;
    @Autowired
    protected TransformationFactory transformationFactory;
    @Autowired
    protected BatchRepositoryPort batches;
    @Autowired
    protected TransformationRepositoryPort transformations;
    @Autowired
    protected TransferRepositoryPort transfers;
    @Autowired
    protected MachineRepositoryPort machines;
    @Autowired
    protected CompanyProjectionRepositoryPort companies;

    @BeforeEach
    void resetDatabase() {
        jdbc.sql("""
                TRUNCATE phase_command_log, transformation_outputs, transformation_inputs, transformations, transfer_batches,
                         transfers, batch_compositions, batches, machine_status_history, machines, company_projection,
                         outbox, processed_events, idempotency_keys, audit_log, evidence_upload_queue, evidence
                """).update();
        clock.advance(java.time.Duration.between(clock.instant(), START));
    }

    // ---- Transacciones: cada bloque es una transacción (y un contexto de persistencia) distinta ----

    protected TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    protected <T> T inTx(Supplier<T> work) {
        return tx().execute(status -> work.get());
    }

    protected void inTx(Runnable work) {
        tx().executeWithoutResult(status -> work.run());
    }

    // ---- Datos de partida (por el dominio y los puertos reales) ----

    protected BatchFactory.Registration registration(String kg) {
        return new BatchFactory.Registration("Algodón Pima", "Fibra", composition("Algodón", "70", "Poliéster", "30"),
                Quantity.ofKg(kg), "Piura", "Textiles del Norte", "Fibra limpia");
    }

    /** Registra y guarda un lote de {@link com.nodotextil.trazatex.production.domain.Fixtures#COMPANY}. */
    protected Batch savedBatch(String kg) {
        return savedBatch(COMPANY, kg, START);
    }

    protected Batch savedBatch(UUID companyId, String kg, Instant createdAt) {
        return inTx(() -> {
            Batch batch = batchFactory.register(registration(kg), companyId, OPERATOR, createdAt);
            batches.save(batch);
            return batch;
        });
    }

    protected Batch savedBatch(Composition composition, String kg) {
        return inTx(() -> {
            Batch batch = batchFactory.register(new BatchFactory.Registration(null, "Fibra", composition,
                    Quantity.ofKg(kg), "Piura", null, "ok"), COMPANY, OPERATOR, START);
            batches.save(batch);
            return batch;
        });
    }

    protected Machine savedMachine(UUID companyId, String code) {
        return inTx(() -> {
            Machine machine = Machine.register(UUID.randomUUID(), companyId, code, "Cardadora " + code, "Cardado", START);
            machines.save(machine);
            return machine;
        });
    }

    protected void saveCompany(UUID companyId, CompanyStatus status) {
        inTx(() -> companies.save(CompanyProjection.of(companyId, "Empresa " + companyId.toString().substring(30), status, START)));
    }

    /**
     * Inserta {@code n} lotes registrados de la empresa por SQL (rápido: para volumen), del más reciente al más antiguo
     * (un segundo de diferencia), con Traceability ID y QR de dígitos y una composición 100 % algodón.
     */
    protected void bulkInsertBatches(UUID companyId, int n, int firstNumber) {
        jdbc.sql("""
                INSERT INTO batches (id, traceability_id, qr_code, company_id, name, material_type, quantity_kg, current_stage,
                                     quality_status, origin_type, origin, reception_characteristics, created_by, created_at)
                SELECT gen_random_uuid(), 'TT-' || lpad((:first + g)::text, 10, '0'), lpad((:first + g)::text, 26, '0'), :company,
                       'Lote ' || g, 'Fibra', 10, 'AVAILABLE', 'NOT_REVIEWED', 'REGISTERED', 'Piura', 'ok',
                       '00000000-0000-0000-0000-0000000000a1', :start - make_interval(secs => g)
                FROM generate_series(0, :n - 1) AS g
                """)
                .param("first", firstNumber).param("company", companyId).param("n", n)
                .param("start", java.time.OffsetDateTime.ofInstant(START, java.time.ZoneOffset.UTC)).update();
        jdbc.sql("INSERT INTO batch_compositions (batch_id, position, component_name, percentage) "
                + "SELECT id, 0, 'Algodón', 100 FROM batches WHERE company_id = :company AND id NOT IN (SELECT batch_id FROM batch_compositions)")
                .param("company", companyId).update();
    }

    protected long count(String sql, Object... params) {
        var statement = jdbc.sql(sql);
        for (Object param : params) {
            statement = statement.param(param);
        }
        return statement.query(Long.class).single();
    }
}
