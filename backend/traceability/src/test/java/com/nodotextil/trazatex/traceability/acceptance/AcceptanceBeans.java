package com.nodotextil.trazatex.traceability.acceptance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.neo4j.core.Neo4jClient;

/** Los colaboradores de los pasos. Solo existen en el contexto de los escenarios (no se detectan por el escaneo de la aplicación). */
@TestConfiguration
class AcceptanceBeans {

    @Bean
    Platform platform(Neo4jClient neo4j) {
        return new Platform(neo4j);
    }

    @Bean
    Api api(MockMvc mockMvc, ObjectMapper json) {
        return new Api(mockMvc, json);
    }

    /**
     * Un {@code World} nuevo por escenario, SIN proxy: {@code @ScenarioScope} envuelve el bean en un proxy de clase y los campos públicos de
     * un proxy no son los del objeto real. Todos los que lo reciben (pasos y hooks) son del mismo ámbito de Cucumber, así que no hace falta.
     */
    @Bean
    @Scope("cucumber-glue")
    World world() {
        return new World();
    }
}
