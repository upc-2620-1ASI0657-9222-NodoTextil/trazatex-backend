package com.nodotextil.trazatex.production.integration;

import org.testcontainers.postgresql.PostgreSQLContainer;

public final class SharedPostgres {

    public static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:16.15");

    static {
        CONTAINER.start();
    }

    private SharedPostgres() {
    }
}
