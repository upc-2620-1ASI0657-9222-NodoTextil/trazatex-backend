-- production: tablas de dominio (docs/domain/production.md, Figura 39) y estructuras técnicas (docs/architecture/data.md)
-- Las cantidades son kg con 3 decimales; los porcentajes, 2 decimales (Quantity / Composition del dominio).
-- Sin claves foráneas hacia las empresas: company_id viene de eventos de organization-access (database per service).

-- Proyección local de empresas (CompanyRegistered / CompanyStatusChanged)
CREATE TABLE company_projection (
    company_id    UUID PRIMARY KEY,
    business_name VARCHAR(200) NOT NULL,
    status        VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    updated_at    TIMESTAMPTZ NOT NULL
);

-- Maquinaria (US10)
CREATE TABLE machines (
    id            UUID PRIMARY KEY,
    company_id    UUID NOT NULL,
    internal_code VARCHAR(50) NOT NULL,
    name          VARCHAR(150) NOT NULL,
    type          VARCHAR(100) NOT NULL,
    status        VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'OUT_OF_SERVICE', 'INACTIVE')),
    created_at    TIMESTAMPTZ NOT NULL
);
-- El código es único por empresa sin distinguir mayúsculas ("MQ-01" y "mq-01" serían la misma máquina)
CREATE UNIQUE INDEX ux_machines_company_code ON machines (company_id, lower(internal_code));

-- Historial de estados: la primera fila (registro) tiene from_status nulo
CREATE TABLE machine_status_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    machine_id  UUID NOT NULL REFERENCES machines (id),
    from_status VARCHAR(20) CHECK (from_status IN ('ACTIVE', 'OUT_OF_SERVICE', 'INACTIVE')),
    to_status   VARCHAR(20) NOT NULL CHECK (to_status IN ('ACTIVE', 'OUT_OF_SERVICE', 'INACTIVE')),
    changed_by  UUID NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_machine_status_history_machine ON machine_status_history (machine_id, id);

-- Lotes (US12). `current_stage` es la fase operativa (BatchPhase). `version` es el bloqueo optimista.
CREATE TABLE batches (
    id                        UUID PRIMARY KEY,
    traceability_id           VARCHAR(13) NOT NULL CHECK (traceability_id ~ '^TT-[0-9A-HJKMNP-TV-Z]{10}$'),
    qr_code                   VARCHAR(26) NOT NULL CHECK (qr_code ~ '^[0-9A-HJKMNP-TV-Z]{26}$'),
    company_id                UUID NOT NULL,
    name                      VARCHAR(200) NOT NULL,
    material_type             VARCHAR(100) NOT NULL,
    quantity_kg               NUMERIC(12, 3) NOT NULL CHECK (quantity_kg > 0),
    current_stage             VARCHAR(30) NOT NULL CHECK (current_stage IN
        ('AVAILABLE', 'IN_TRANSFORMATION', 'IN_TRANSFER', 'IN_QUALITY_CONTROL', 'PROCESSED', 'SPLIT', 'DISCARDED')),
    quality_status            VARCHAR(30) NOT NULL CHECK (quality_status IN
        ('NOT_REVIEWED', 'CONFORMING', 'FAILED', 'PENDING_REEVALUATION', 'POTENTIAL_DERIVED_FAILURE')),
    quality_hold              BOOLEAN NOT NULL DEFAULT FALSE,
    final_product             BOOLEAN NOT NULL DEFAULT FALSE,
    origin_type               VARCHAR(20) NOT NULL CHECK (origin_type IN ('REGISTERED', 'SPLIT', 'TRANSFORMATION')),
    origin                    VARCHAR(200),
    supplier_name             VARCHAR(200),
    reception_characteristics TEXT,
    created_by                UUID NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL,
    buyer_or_distributor      VARCHAR(200),
    sale_price                NUMERIC(14, 2) CHECK (sale_price >= 0),
    currency                  VARCHAR(3) CHECK (currency ~ '^[A-Z]{3}$'),
    commercial_exit_date      DATE,
    commercial_reference      VARCHAR(200),
    version                   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_batches_traceability_id UNIQUE (traceability_id),
    CONSTRAINT ux_batches_qr_code UNIQUE (qr_code),
    -- un lote registrado exige origen y características de recepción (US12)
    CONSTRAINT ck_batches_registered_data CHECK (origin_type <> 'REGISTERED' OR (origin IS NOT NULL AND reception_characteristics IS NOT NULL)),
    -- los datos comerciales son solo de un producto final (US20) y un precio exige moneda
    CONSTRAINT ck_batches_commercial_only_final CHECK (final_product OR
        (buyer_or_distributor IS NULL AND sale_price IS NULL AND currency IS NULL AND commercial_exit_date IS NULL AND commercial_reference IS NULL)),
    CONSTRAINT ck_batches_price_needs_currency CHECK (sale_price IS NULL OR currency IS NOT NULL)
);
CREATE INDEX ix_batches_company_stage ON batches (company_id, current_stage, created_at DESC);
CREATE INDEX ix_batches_company_created ON batches (company_id, created_at DESC, id);

CREATE TABLE batch_compositions (
    batch_id       UUID NOT NULL REFERENCES batches (id),
    position       INT NOT NULL CHECK (position >= 0),
    component_name VARCHAR(80) NOT NULL,
    percentage     NUMERIC(5, 2) NOT NULL CHECK (percentage > 0 AND percentage <= 100),
    PRIMARY KEY (batch_id, position)
);

-- Transformaciones (US16–US19). Cuando está COMPLETED el balance de masa cierra también en la base.
CREATE TABLE transformations (
    id                  UUID PRIMARY KEY,
    company_id          UUID NOT NULL,
    transformation_type VARCHAR(20) NOT NULL CHECK (transformation_type IN ('SPINNING', 'WEAVING', 'DYEING')),
    machinery_id        UUID NOT NULL REFERENCES machines (id),
    operator_id         UUID NOT NULL,
    status              VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    total_input_kg      NUMERIC(15, 3) NOT NULL CHECK (total_input_kg > 0),
    total_output_kg     NUMERIC(15, 3) CHECK (total_output_kg > 0),
    waste_kg            NUMERIC(15, 3) CHECK (waste_kg >= 0),
    waste_reason        TEXT,
    shrinkage_kg        NUMERIC(15, 3) CHECK (shrinkage_kg >= 0),
    start_time          TIMESTAMPTZ NOT NULL,
    end_time            TIMESTAMPTZ,
    CONSTRAINT ck_transformations_completed CHECK (
        (status = 'IN_PROGRESS' AND total_output_kg IS NULL AND waste_kg IS NULL AND shrinkage_kg IS NULL AND end_time IS NULL)
        OR (status = 'COMPLETED' AND total_output_kg IS NOT NULL AND waste_kg IS NOT NULL AND shrinkage_kg IS NOT NULL
            AND end_time IS NOT NULL AND total_input_kg = total_output_kg + waste_kg + shrinkage_kg)),
    CONSTRAINT ck_transformations_waste_reason CHECK (waste_kg IS NULL OR waste_kg = 0 OR (waste_reason IS NOT NULL AND length(btrim(waste_reason)) > 0))
);
CREATE INDEX ix_transformations_company ON transformations (company_id, start_time DESC);

-- Un lote entra a una sola transformación (se consume completo)
CREATE TABLE transformation_inputs (
    transformation_id UUID NOT NULL REFERENCES transformations (id),
    position          INT NOT NULL CHECK (position >= 0),
    batch_id          UUID NOT NULL REFERENCES batches (id),
    quantity_kg       NUMERIC(12, 3) NOT NULL CHECK (quantity_kg > 0),
    PRIMARY KEY (transformation_id, position),
    CONSTRAINT ux_transformation_inputs_batch UNIQUE (batch_id)
);

-- Un lote de salida nace de una sola transformación
CREATE TABLE transformation_outputs (
    transformation_id UUID NOT NULL REFERENCES transformations (id),
    position          INT NOT NULL CHECK (position >= 0),
    batch_id          UUID NOT NULL REFERENCES batches (id),
    PRIMARY KEY (transformation_id, position),
    CONSTRAINT ux_transformation_outputs_batch UNIQUE (batch_id)
);

-- Transferencias (US21, US22)
CREATE TABLE transfers (
    id                UUID PRIMARY KEY,
    origin_company_id UUID NOT NULL,
    target_company_id UUID NOT NULL,
    status            VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    departure_time    TIMESTAMPTZ NOT NULL,
    resolved_at       TIMESTAMPTZ,
    rejection_reason  TEXT,
    initiated_by      UUID NOT NULL,
    resolved_by       UUID,
    CONSTRAINT ck_transfers_different_companies CHECK (origin_company_id <> target_company_id),
    CONSTRAINT ck_transfers_resolution CHECK (
        (status = 'PENDING' AND resolved_at IS NULL AND resolved_by IS NULL AND rejection_reason IS NULL)
        OR (status = 'ACCEPTED' AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL AND rejection_reason IS NULL)
        OR (status = 'REJECTED' AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL
            AND rejection_reason IS NOT NULL AND length(btrim(rejection_reason)) > 0))
);
CREATE INDEX ix_transfers_origin ON transfers (origin_company_id, departure_time DESC);
CREATE INDEX ix_transfers_target ON transfers (target_company_id, status, departure_time DESC);

CREATE TABLE transfer_batches (
    transfer_id UUID NOT NULL REFERENCES transfers (id),
    position    INT NOT NULL CHECK (position >= 0),
    batch_id    UUID NOT NULL REFERENCES batches (id),
    PRIMARY KEY (transfer_id, position),
    CONSTRAINT ux_transfer_batches UNIQUE (transfer_id, batch_id)
);
CREATE INDEX ix_transfer_batches_batch ON transfer_batches (batch_id);

-- Comando interno de Quality (ADR-009): idempotente por (batch_id, request_id, operation).
-- Guarda también la retención resultante para que repetir el comando devuelva exactamente lo mismo.
CREATE TABLE phase_command_log (
    batch_id            UUID NOT NULL REFERENCES batches (id),
    request_id          VARCHAR(64) NOT NULL,
    operation           VARCHAR(20) NOT NULL CHECK (operation IN ('START_EVALUATION', 'RELEASE', 'DISCARD', 'HOLD', 'RELEASE_HOLD')),
    result_phase        VARCHAR(30) NOT NULL,
    result_quality_hold BOOLEAN NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (batch_id, request_id, operation)
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

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

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
CREATE INDEX ix_audit_log_company ON audit_log (company_id, occurred_at);
