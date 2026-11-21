package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.sharedtesting.TrazatexNeo4jContainer;
import org.testcontainers.neo4j.Neo4jContainer;

public final class SharedNeo4j {

    public static final String PASSWORD = "trazatex-test-password";

    public static final Neo4jContainer CONTAINER = new TrazatexNeo4jContainer(PASSWORD).withReuse(true);

    static {
        CONTAINER.start();
    }

    private SharedNeo4j() {
    }
}
