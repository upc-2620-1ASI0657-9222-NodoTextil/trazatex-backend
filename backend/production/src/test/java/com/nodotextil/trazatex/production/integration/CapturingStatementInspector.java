package com.nodotextil.trazatex.production.integration;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Guarda el SQL real que Hibernate envía a la base (se registra con la propiedad
 * {@code hibernate.session_factory.statement_inspector}). Permite comprobar con EXPLAIN la consulta de verdad y no una
 * parecida escrita a mano.
 */
public class CapturingStatementInspector implements StatementInspector {

    private static final int LIMIT = 2000;
    private static final List<String> CAPTURED = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        if (CAPTURED.size() > LIMIT) {
            CAPTURED.clear();
        }
        CAPTURED.add(sql);
        return sql;
    }

    public static void reset() {
        CAPTURED.clear();
    }

    public static List<String> captured() {
        return List.copyOf(CAPTURED);
    }
}
