package com.nodotextil.trazatex.traceability.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base de las pruebas de integración: Spring completo + Neo4j real (Testcontainers, una sola instancia para toda la corrida).
 * Cada prueba parte de un grafo vacío; las restricciones (que crea el servicio al arrancar) se conservan.
 */
@SpringBootTest(properties = "trazatex.messaging.enabled=false")
@AutoConfigureMockMvc
abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void neo4j(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", SharedNeo4j.CONTAINER::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> SharedNeo4j.PASSWORD);
    }

    @Autowired
    protected Neo4jClient neo4j;

    @BeforeEach
    void emptyGraph() {
        neo4j.query("MATCH (n) DETACH DELETE n").run();
    }
}
