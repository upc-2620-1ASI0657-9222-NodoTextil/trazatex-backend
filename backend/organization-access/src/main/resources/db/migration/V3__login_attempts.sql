-- Intentos fallidos de login por correo normalizado (minúsculas), exista o no la cuenta (RNF-06): el bloqueo de 15 minutos
-- se comporta igual con correos inexistentes y la respuesta no revela si una cuenta existe.
CREATE TABLE login_attempts (
    email           VARCHAR(254) PRIMARY KEY CHECK (email = lower(email)),
    failed_attempts INT NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_login_attempts_locked ON login_attempts (locked_until) WHERE locked_until IS NOT NULL;
CREATE INDEX ix_login_attempts_updated ON login_attempts (updated_at);

-- Lo que había en users pasa a la tabla nueva
INSERT INTO login_attempts (email, failed_attempts, locked_until, updated_at)
SELECT email, failed_login_attempts, locked_until, now()
FROM users WHERE failed_login_attempts > 0 OR locked_until IS NOT NULL;

ALTER TABLE users DROP COLUMN failed_login_attempts;
ALTER TABLE users DROP COLUMN locked_until;
