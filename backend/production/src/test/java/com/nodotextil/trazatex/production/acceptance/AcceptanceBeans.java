package com.nodotextil.trazatex.production.acceptance;

import org.springframework.core.env.Environment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

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

    @Bean
    @Scope("cucumber-glue")
    World world() {
        return new World();
    }
}
