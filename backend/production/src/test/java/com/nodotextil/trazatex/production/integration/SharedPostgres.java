package com.nodotextil.trazatex.production.integration;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** El PostgreSQL de las pruebas: un solo contenedor por JVM, compartido por las pruebas de integración y por los escenarios de aceptación. */
public final class SharedPostgres {

    public static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:16.15");

    static {
        CONTAINER.start();
    }

    private SharedPostgres() {
    }
}
