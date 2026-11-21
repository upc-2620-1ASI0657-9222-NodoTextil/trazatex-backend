package com.nodotextil.trazatex.production.integration;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
