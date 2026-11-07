package com.nodotextil.trazatex.traceability.acceptance;

import com.nodotextil.trazatex.traceability.integration.SharedNeo4j;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El contexto de los escenarios (RNF-10): el servicio completo con sus cinco consumidores, Neo4j y Kafka reales (Testcontainers). Los
 * pasos hacen de los demás servicios (publican sus eventos en los tópicos), consultan la API como la llama el Gateway (con las cabeceras de identidad)
 * y miran el grafo con Cypher; sin dobles del dominio, de los casos de uso ni de los repositorios.
 */
@CucumberContextConfiguration
@SpringBootTest(properties = {"trazatex.messaging.enabled=true", "trazatex.messaging.consumer.topic-check-interval=PT0.5S"})
@AutoConfigureMockMvc
@Import(AcceptanceBeans.class)
public class AcceptanceSpringConfiguration {

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", SharedNeo4j.CONTAINER::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> SharedNeo4j.PASSWORD);
        registry.add("trazatex.messaging.bootstrap-servers", AcceptanceKafka.CONTAINER::getBootstrapServers);
    }
}
