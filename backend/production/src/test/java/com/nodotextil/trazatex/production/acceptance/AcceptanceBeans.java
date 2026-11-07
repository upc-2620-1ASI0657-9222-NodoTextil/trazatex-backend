package com.nodotextil.trazatex.production.acceptance;

import org.springframework.core.env.Environment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

/** Los colaboradores de los pasos. Solo existen en el contexto de los escenarios (no se detectan por el escaneo de la aplicación). */
@TestConfiguration
class AcceptanceBeans {

    @Bean
    ProductionApi productionApi(Environment environment, ObjectMapper json) {
        return new ProductionApi(environment, json);
    }

    @Bean
    Outbox outbox(JdbcClient jdbc, ObjectMapper json) {
        return new Outbox(jdbc, json);
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
