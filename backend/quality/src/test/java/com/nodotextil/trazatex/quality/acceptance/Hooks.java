package com.nodotextil.trazatex.quality.acceptance;

import com.nodotextil.trazatex.quality.integration.CloudinaryStub;
import com.nodotextil.trazatex.quality.integration.PdfSwitch;
import com.nodotextil.trazatex.quality.integration.ProductionStub;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Cada escenario empieza con la base vacía, el circuito cerrado y Production respondiendo bien. */
public class Hooks {

    private final JdbcClient jdbc;
    private final CircuitBreakerRegistry circuitBreakers;
    private final World world;

    public Hooks(JdbcClient jdbc, CircuitBreakerRegistry circuitBreakers, World world) {
        this.jdbc = jdbc;
        this.circuitBreakers = circuitBreakers;
        this.world = world;
    }

    @Before(order = 0)
    public void cleanAndPrepare(Scenario scenario) {
        jdbc.sql("TRUNCATE evidence_upload_queue, evidence, quality_failure, impacted_batch, outbox, quality_test, quality_control, batch_projection, processed_events, idempotency_keys, audit_log")
                .update();
        ProductionStub.reset();
        ProductionStub.answersOk();
        circuitBreakers.circuitBreaker("production").reset();
        CloudinaryStub.reset();
        circuitBreakers.circuitBreaker("cloudinary").reset();
        PdfSwitch.start();
        // El URI es opaco (classpath:features/us25-finalizar-control-de-calidad.feature): la historia es lo que va antes del primer guion del nombre
        String name = scenario.getUri().toString();
        name = name.substring(name.lastIndexOf('/') + 1);
        world.story = name.substring(0, name.indexOf('-'));
    }
}
