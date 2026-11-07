package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.application.port.out.LockWaitLimitPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** {@code SET LOCAL lock_timeout}: vale solo para la transacción en curso. */
@Component
class JdbcLockWaitLimitAdapter implements LockWaitLimitPort {

    private final JdbcClient jdbc;

    JdbcLockWaitLimitAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void limit(Duration wait) {
        // SET no admite parámetros: el valor es un entero que sale de una Duration, no de la petición
        jdbc.sql("SET LOCAL lock_timeout = '" + Math.max(1, wait.toMillis()) + "ms'").update();
    }
}
