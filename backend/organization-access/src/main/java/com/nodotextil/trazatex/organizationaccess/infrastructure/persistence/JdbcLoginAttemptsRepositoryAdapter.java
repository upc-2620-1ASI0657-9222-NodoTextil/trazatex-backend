package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.out.LoginAttemptsRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.LoginAttempts;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

@Component
class JdbcLoginAttemptsRepositoryAdapter implements LoginAttemptsRepositoryPort {

    private final JdbcClient jdbc;

    JdbcLoginAttemptsRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LoginAttempts lockOrCreate(Email email, Instant now) {
        jdbc.sql("""
                        INSERT INTO login_attempts (email, failed_attempts, locked_until, updated_at)
                        VALUES (:email, 0, NULL, :now)
                        ON CONFLICT (email) DO NOTHING
                        """)
                .param("email", email.value())
                .param("now", Timestamps.toDb(now))
                .update();
        return jdbc.sql("SELECT failed_attempts, locked_until, updated_at FROM login_attempts WHERE email = :email FOR UPDATE")
                .param("email", email.value())
                .query((rs, row) -> LoginAttempts.restore(email, rs.getInt("failed_attempts"),
                        Timestamps.fromDb(rs.getObject("locked_until", OffsetDateTime.class)),
                        Timestamps.fromDb(rs.getObject("updated_at", OffsetDateTime.class))))
                .single();
    }

    @Override
    public void save(LoginAttempts attempts) {
        jdbc.sql("UPDATE login_attempts SET failed_attempts = :failed, locked_until = :lockedUntil, updated_at = :updatedAt WHERE email = :email")
                .param("failed", attempts.failedAttempts())
                .param("lockedUntil", Timestamps.toDb(attempts.lockedUntil()), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("updatedAt", Timestamps.toDb(attempts.updatedAt()))
                .param("email", attempts.email().value())
                .update();
    }

    @Override
    public void delete(Email email) {
        jdbc.sql("DELETE FROM login_attempts WHERE email = :email").param("email", email.value()).update();
    }

    @Override
    public int deleteExpired(Instant now, Duration idleRetention) {
        return jdbc.sql("""
                        DELETE FROM login_attempts
                        WHERE (locked_until IS NOT NULL AND locked_until <= :now)
                           OR (locked_until IS NULL AND updated_at < :cutoff)
                        """)
                .param("now", Timestamps.toDb(now))
                .param("cutoff", Timestamps.toDb(now.minus(idleRetention)))
                .update();
    }
}
