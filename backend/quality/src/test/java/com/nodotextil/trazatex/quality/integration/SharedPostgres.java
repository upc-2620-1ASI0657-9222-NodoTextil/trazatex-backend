package com.nodotextil.trazatex.quality.integration;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** El PostgreSQL de las pruebas: un solo contenedor por JVM, compartido por las pruebas de integración. */
public final class SharedPostgres {

    public static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:16.15");

    static {
        CONTAINER.start();
    }

    private SharedPostgres() {
    }
}
