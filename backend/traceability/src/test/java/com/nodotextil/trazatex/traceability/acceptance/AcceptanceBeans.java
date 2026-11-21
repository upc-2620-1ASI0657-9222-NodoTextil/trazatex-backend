package com.nodotextil.trazatex.traceability.acceptance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.neo4j.core.Neo4jClient;

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

    @Bean
    @Scope("cucumber-glue")
    World world() {
        return new World();
    }
}
