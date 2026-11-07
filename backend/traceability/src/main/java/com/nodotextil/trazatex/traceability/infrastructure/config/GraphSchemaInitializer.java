package com.nodotextil.trazatex.traceability.infrastructure.config;

import com.nodotextil.trazatex.traceability.infrastructure.persistence.GraphSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Crea las restricciones de unicidad y los índices del grafo al iniciar, <b>antes</b> de que arranque ningún consumidor
 * (corre al terminar de crear los beans, y los listeners de Kafka arrancan después). Es idempotente ({@code IF NOT EXISTS}).
 * Si Neo4j no responde, o ya hay datos que violan una restricción, el servicio no arranca y el mensaje dice por qué:
 * sin ellas, dos consumidores concurrentes podrían crear dos nodos para el mismo lote.
 */
@Component
public class GraphSchemaInitializer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(GraphSchemaInitializer.class);

    private final Neo4jClient neo4j;

    GraphSchemaInitializer(Neo4jClient neo4j) {
        this.neo4j = neo4j;
    }

    @Override
    public void afterSingletonsInstantiated() {
        ensureSchema();
    }

    public void ensureSchema() {
        List<String> statements = new ArrayList<>();
        for (GraphSchema.Statement constraint : GraphSchema.CONSTRAINTS) {
            run(constraint.name(), constraint.cypher(), "la restricción");
            statements.add(constraint.name());
        }
        for (GraphSchema.Statement index : GraphSchema.INDEXES) {
            run(index.name(), index.cypher(), "el índice");
            statements.add(index.name());
        }
        neo4j.query("CALL db.awaitIndexes(60)").run();
        log.info("Esquema de Neo4j listo: {}", statements);
    }

    private void run(String name, String cypher, String what) {
        try {
            neo4j.query(cypher).run();
        } catch (RuntimeException e) {
            throw new IllegalStateException("No se pudo crear " + what + " '" + name + "' en Neo4j: " + rootMessage(e)
                    + ". Revise que Neo4j esté accesible (NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD) y que los datos existentes la cumplan.", e);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
