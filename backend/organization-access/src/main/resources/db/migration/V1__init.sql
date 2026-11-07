-- organization-access: tablas de dominio (docs/domain/organization-access.md) y estructuras técnicas (docs/architecture/data.md)

-- Roles sembrados (Figura 43). La autorización se hace por rol; `permissions` queda para futuros permisos finos.
CREATE TABLE roles (
    id          SMALLINT PRIMARY KEY,
    role_name   VARCHAR(30) NOT NULL UNIQUE,
    permissions JSONB NOT NULL DEFAULT '[]'::jsonb
);
INSERT INTO roles (id, role_name) VALUES (1, 'LICENSE_OWNER'), (2, 'COMPANY_ADMIN'), (3, 'OPERATOR');

-- Una sola licencia (la crea el bootstrap): la columna `singleton` con UNIQUE impide una segunda fila.
CREATE TABLE license (
    id                    UUID PRIMARY KEY,
    max_companies         INT NOT NULL CHECK (max_companies >= 1),
    max_users_per_company INT NOT NULL CHECK (max_users_per_company >= 1),
    created_at            TIMESTAMPTZ NOT NULL,
    singleton             BOOLEAN NOT NULL DEFAULT TRUE CHECK (singleton),
    CONSTRAINT ux_license_singleton UNIQUE (singleton)
);

CREATE TABLE companies (
    id            UUID PRIMARY KEY,
    ruc           VARCHAR(11) NOT NULL CHECK (ruc ~ '^[0-9]{11}$'),
    business_name VARCHAR(200) NOT NULL,
    status        VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_companies_ruc UNIQUE (ruc)
);

CREATE TABLE company_activities (
    company_id UUID NOT NULL REFERENCES companies (id),
    activity   VARCHAR(20) NOT NULL CHECK (activity IN ('SPINNING', 'WEAVING', 'DYEING', 'FINISHING', 'CUTTING', 'SEWING', 'BRAND')),
    PRIMARY KEY (company_id, activity)
);

CREATE TABLE users (
    id                    UUID PRIMARY KEY,
    company_id            UUID REFERENCES companies (id),
    email                 VARCHAR(254) NOT NULL,
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    position              VARCHAR(100),
    password_hash         VARCHAR(100) NOT NULL,
    role                  VARCHAR(30) NOT NULL REFERENCES roles (role_name),
    status                VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    -- Solo el Dueño de Licencia carece de empresa
    CONSTRAINT ck_users_company_by_role CHECK ((role = 'LICENSE_OWNER') = (company_id IS NULL))
);
CREATE INDEX ix_users_company ON users (company_id, status);
CREATE INDEX ix_users_owner ON users (role, status) WHERE role = 'LICENSE_OWNER';

CREATE TABLE invitations (
    id         UUID PRIMARY KEY,
    token      VARCHAR(128) NOT NULL,
    email      VARCHAR(254) NOT NULL,
    first_name VARCHAR(100),
    last_name  VARCHAR(100),
    position   VARCHAR(100),
    role       VARCHAR(30) NOT NULL REFERENCES roles (role_name),
    company_id UUID REFERENCES companies (id),
    issued_by  UUID REFERENCES users (id),          -- nulo solo en la invitación del Dueño que crea el bootstrap
    status     VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_invitations_token UNIQUE (token),
    CONSTRAINT ck_invitations_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_invitations_company_by_role CHECK ((role = 'LICENSE_OWNER') = (company_id IS NULL))
);
-- Un correo no puede tener dos invitaciones PENDING. Una vencida que el job aún no marcó sigue ocupando este índice:
-- al invitar, la aplicación la marca EXPIRED antes de insertar la nueva.
CREATE UNIQUE INDEX ux_invitations_pending_email ON invitations (email) WHERE status = 'PENDING';
CREATE INDEX ix_invitations_pending_company ON invitations (company_id, expires_at) WHERE status = 'PENDING';
CREATE INDEX ix_invitations_pending_expiry ON invitations (expires_at) WHERE status = 'PENDING';

CREATE TABLE admin_requests (
    id           UUID PRIMARY KEY,
    company_id   UUID NOT NULL REFERENCES companies (id),
    requested_by UUID NOT NULL REFERENCES users (id),
    full_name    VARCHAR(200) NOT NULL,
    email        VARCHAR(254) NOT NULL,
    position     VARCHAR(100) NOT NULL,
    status       VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at   TIMESTAMPTZ NOT NULL,
    resolved_at  TIMESTAMPTZ,
    resolved_by  UUID REFERENCES users (id)
);
CREATE INDEX ix_admin_requests_company ON admin_requests (company_id, status);

CREATE TABLE privacy_settings (
    company_id UUID NOT NULL REFERENCES companies (id),
    field      VARCHAR(40) NOT NULL CHECK (field IN ('SUPPLIER', 'ORIGIN', 'QUANTITY', 'COMPOSITION', 'RECEPTION_CHARACTERISTICS',
                                                      'MACHINERY', 'SHRINKAGE', 'WASTE', 'WASTE_REASON', 'FAILURE_CAUSE', 'QUALITY_TEST_VALUES')),
    visibility VARCHAR(10) NOT NULL CHECK (visibility IN ('SHARED', 'PRIVATE')),
    PRIMARY KEY (company_id, field)
);

-- Estructuras técnicas (data.md)
CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id   VARCHAR(80) NOT NULL,
    event_type     VARCHAR(80) NOT NULL,
    topic          VARCHAR(120) NOT NULL,
    payload        JSONB NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED')),
    attempts       INT NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ NOT NULL,   -- lo escribe la aplicación (OutboxEventRecorder), no now()
    published_at   TIMESTAMPTZ
);
CREATE INDEX ix_outbox_pending ON outbox (status, created_at);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(64) PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_status INT NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_idempotency_keys_created ON idempotency_keys (created_at);

CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    company_id  UUID,
    action      VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id   VARCHAR(80) NOT NULL,
    changes     JSONB,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);

-- Caché de la validación de RUC (ADR-012)
CREATE TABLE validated_ruc_cache (
    ruc                VARCHAR(11) PRIMARY KEY CHECK (ruc ~ '^[0-9]{11}$'),
    business_name      VARCHAR(300) NOT NULL,
    taxpayer_status    VARCHAR(40) NOT NULL,
    domicile_condition VARCHAR(40) NOT NULL,
    validated_at       TIMESTAMPTZ NOT NULL
);
