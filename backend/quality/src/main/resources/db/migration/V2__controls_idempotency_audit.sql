-- Controles de calidad (docs/domain/quality.md) y lo técnico de 06b: claves de idempotencia (ADR-010) y auditoría.

CREATE TABLE quality_control (
    id                    UUID PRIMARY KEY,
    batch_id              UUID         NOT NULL,
    company_id            UUID         NOT NULL,
    control_type          VARCHAR(20)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    overall_result        VARCHAR(20),
    inspector_id          UUID         NOT NULL,
    start_date            TIMESTAMPTZ  NOT NULL,
    end_date              TIMESTAMPTZ,
    -- siempre privadas: no viajan a otras empresas
    internal_observations TEXT,
    -- código de error con el que Production rechazó el inicio (409, 404 o un 400): el motivo de la cancelación
    cancel_reason         VARCHAR(100),
    -- reintento del comando START_EVALUATION mientras el control está INICIANDO (PendingControlRecoveryJob):
    -- next_attempt_at es la espera hasta el próximo intento y, mientras alguien lo está intentando, su arrendamiento (lease)
    attempts              INT          NOT NULL DEFAULT 0,
    next_attempt_at       TIMESTAMPTZ,
    last_error            TEXT,
    CONSTRAINT quality_control_type_check CHECK (control_type IN ('EVALUATION', 'REEVALUATION')),
    CONSTRAINT quality_control_status_check CHECK (status IN ('STARTING', 'IN_PROGRESS', 'FINISHED', 'CANCELLED')),
    CONSTRAINT quality_control_result_check CHECK (overall_result IS NULL OR overall_result IN ('CONFORMING', 'FAILED')),
    CONSTRAINT quality_control_cancel_reason_check CHECK ((status = 'CANCELLED') = (cancel_reason IS NOT NULL)),
    CONSTRAINT quality_control_pending_check CHECK ((status = 'STARTING') = (next_attempt_at IS NOT NULL))
);

-- Un solo control activo por lote (US23 esc. 3): dos inicios simultáneos no pasan los dos
CREATE UNIQUE INDEX ux_quality_control_active ON quality_control (batch_id) WHERE status IN ('STARTING', 'IN_PROGRESS');
-- Los controles INICIANDO que el job reclama (FOR UPDATE SKIP LOCKED)
CREATE INDEX ix_quality_control_pending ON quality_control (next_attempt_at, id) WHERE status = 'STARTING';
CREATE INDEX ix_quality_control_batch ON quality_control (batch_id, start_date);
CREATE INDEX ix_quality_control_company ON quality_control (company_id, start_date);

CREATE TABLE quality_test (
    id             UUID PRIMARY KEY,
    control_id     UUID         NOT NULL REFERENCES quality_control (id),
    seq            BIGSERIAL    NOT NULL,
    criterion_name VARCHAR(200) NOT NULL,
    expected_value VARCHAR(200) NOT NULL,
    obtained_value VARCHAR(200) NOT NULL,
    unit           VARCHAR(50)  NOT NULL,
    test_result    VARCHAR(20)  NOT NULL,
    remarks        TEXT,
    CONSTRAINT quality_test_result_check CHECK (test_result IN ('CONFORMING', 'FAILED'))
);
CREATE INDEX ix_quality_test_control ON quality_test (control_id, seq);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(64) PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_status INT         NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_idempotency_keys_created ON idempotency_keys (created_at);

CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    -- nulo = acción del sistema (p. ej. un HOLD por una falla derivada, que dispara un evento y no una persona; llega en 06c)
    user_id     UUID,
    company_id  UUID,
    action      VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id   VARCHAR(80) NOT NULL,
    changes     JSONB,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_log_company ON audit_log (company_id, occurred_at);
