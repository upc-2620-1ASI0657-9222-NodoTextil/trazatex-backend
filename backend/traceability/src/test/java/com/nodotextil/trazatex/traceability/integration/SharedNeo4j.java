package com.nodotextil.trazatex.traceability.integration;

import com.nodotextil.trazatex.sharedtesting.TrazatexNeo4jContainer;
import org.testcontainers.neo4j.Neo4jContainer;

/**
 * El Neo4j de las pruebas: un solo contenedor por JVM, con la misma imagen y la misma memoria (heap de 512 MB) que el docker compose,
 * para que las restricciones, las consultas y el rendimiento se comporten como en local (ver {@code TrazatexNeo4jContainer}).
 */
public final class SharedNeo4j {

    public static final String PASSWORD = "trazatex-test-password";

    /**
     * {@code withReuse(true)} no cambia nada en una corrida normal (Testcontainers solo lo respeta si {@code TESTCONTAINERS_REUSE_ENABLE=true} está en el entorno, y el
     * contenedor se detiene al terminar la JVM). Las corridas de mutaciones, que repiten las mismas pruebas decenas de veces, lo activan para no arrancar un Neo4j en cada una:
     * el contenedor sobrevive a la JVM y se reutiliza (todas las pruebas vacían el grafo al empezar o usan ids propios). Quien lo active debe borrarlo al terminar.
     */
    public static final Neo4jContainer CONTAINER = new TrazatexNeo4jContainer(PASSWORD).withReuse(true);

    static {
        CONTAINER.start();
    }

    private SharedNeo4j() {
    }
}
