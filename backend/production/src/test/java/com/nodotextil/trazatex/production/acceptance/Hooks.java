package com.nodotextil.trazatex.production.acceptance;

import io.cucumber.java.Before;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Cada escenario empieza con la base vacía, las empresas A y B activas (como las dejaría el consumo de eventos de 04d) y Cloudinary respondiendo bien. */
public class Hooks {

    private final JdbcClient jdbc;
    private final CircuitBreakerRegistry circuitBreakers;

    public Hooks(JdbcClient jdbc, CircuitBreakerRegistry circuitBreakers) {
        this.jdbc = jdbc;
        this.circuitBreakers = circuitBreakers;
    }

    @Before(order = 0)
    public void cleanAndSeed() {
        jdbc.sql("""
                TRUNCATE evidence_upload_queue, evidence, phase_command_log, transformation_outputs, transformation_inputs, transformations,
                         transfer_batches, transfers, batch_compositions, batches, machine_status_history, machines, company_projection,
                         outbox, processed_events, idempotency_keys, audit_log
                """).update();
        for (var company : new Object[][]{{World.COMPANY_A, "Hilandería Andina SAC"}, {World.COMPANY_B, "Tintorería del Sur SAC"}}) {
            jdbc.sql("INSERT INTO company_projection (company_id, business_name, status, updated_at) VALUES (:id, :name, 'ACTIVE', :at)")
                    .param("id", company[0]).param("name", company[1]).param("at", OffsetDateTime.now(ZoneOffset.UTC)).update();
        }
        CloudinaryStub.reset();
        circuitBreakers.circuitBreaker("cloudinary").reset();
    }
}
