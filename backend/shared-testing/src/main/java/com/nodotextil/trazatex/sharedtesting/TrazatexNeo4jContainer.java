package com.nodotextil.trazatex.sharedtesting;

import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * El Neo4j de las pruebas: la misma imagen (edición community) y la misma memoria que el del {@code docker-compose.yml}: heap de 512 MB
 * (inicial y máximo) y 256 MB de caché de páginas. Con los valores por omisión de la imagen Neo4j dimensiona la memoria según la máquina
 * (varios GB), así que una prueba de rendimiento (RNF-04) pasaría con holgura que el entorno real no tiene.
 *
 * <p>Toda prueba con Neo4j real usa esta clase en lugar de {@code new Neo4jContainer(...)}. La versión de la imagen y los tres valores
 * de memoria están en un solo lugar y una prueba ({@code TrazatexNeo4jContainerTest}) comprueba que coinciden con los del compose.
 * La contraseña del usuario {@code neo4j} se indica al construirlo.
 */
public class TrazatexNeo4jContainer extends Neo4jContainer {

    /** Misma versión exacta que {@code docker-compose.yml}. */
    public static final String IMAGE = "neo4j:5.26.32-community";

    public TrazatexNeo4jContainer(String adminPassword) {
        super(DockerImageName.parse(IMAGE));
        withAdminPassword(adminPassword);
        withEnv("NEO4J_server_memory_heap_initial__size", "512m");
        withEnv("NEO4J_server_memory_heap_max__size", "512m");
        withEnv("NEO4J_server_memory_pagecache_size", "256m");
    }
}
