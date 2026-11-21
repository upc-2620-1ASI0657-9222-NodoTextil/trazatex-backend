package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.application.port.out.LockWaitLimitPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class JdbcLockWaitLimitAdapter implements LockWaitLimitPort {

    private final JdbcClient jdbc;

    JdbcLockWaitLimitAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void limit(Duration wait) {
        jdbc.sql("SET LOCAL lock_timeout = '" + Math.max(1, wait.toMillis()) + "ms'").update();
    }
}
