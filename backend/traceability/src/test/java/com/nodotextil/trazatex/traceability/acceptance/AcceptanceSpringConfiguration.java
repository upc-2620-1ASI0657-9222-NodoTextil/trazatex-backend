package com.nodotextil.trazatex.traceability.acceptance;

import com.nodotextil.trazatex.traceability.integration.SharedNeo4j;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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
