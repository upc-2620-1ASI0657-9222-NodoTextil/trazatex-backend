package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
class Neo4jLineageSchemaInitializer implements ApplicationRunner {

    private final Neo4jClient client;

    Neo4jLineageSchemaInitializer(Neo4jClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        client.query("CREATE CONSTRAINT lot_batch_id IF NOT EXISTS "
                + "FOR (l:Lot) REQUIRE l.batchId IS UNIQUE").run();
    }
}
