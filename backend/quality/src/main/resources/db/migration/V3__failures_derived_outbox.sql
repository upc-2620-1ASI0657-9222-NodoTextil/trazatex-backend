-- 06c: finalizar y descartar solo con la confirmación de Production, fallas, avisos de falla derivada y outbox.

-- ---- Finalizar un control: la solicitud queda pendiente (el control sigue IN_PROGRESS) hasta que Production confirma RELEASE ----
-- finish_result es el resultado congelado al solicitar (las pruebas ya no cambian): de él sale la retención que viaja en RELEASE,
-- que debe ser la misma en cada reintento. next_attempt_at ahora también lo tiene un control IN_PROGRESS con la finalización pendiente.
ALTER TABLE quality_control
    ADD COLUMN finish_requested_at TIMESTAMPTZ,
    ADD COLUMN finish_requested_by UUID,
    ADD COLUMN finish_result       VARCHAR(20);

ALTER TABLE quality_control DROP CONSTRAINT quality_control_pending_check;
ALTER TABLE quality_control
    ADD CONSTRAINT quality_control_finish_columns_check CHECK (
        (finish_requested_at IS NULL) = (finish_requested_by IS NULL) AND (finish_requested_at IS NULL) = (finish_result IS NULL)),
    ADD CONSTRAINT quality_control_finish_result_check CHECK (finish_result IS NULL OR finish_result IN ('CONFORMING', 'FAILED')),
    -- FINISHED siempre pasó por una solicitud; una cancelada nunca la tuvo
    ADD CONSTRAINT quality_control_finished_check CHECK ((status <> 'FINISHED' OR finish_requested_at IS NOT NULL)
        AND (status <> 'CANCELLED' OR finish_requested_at IS NULL)),
    ADD CONSTRAINT quality_control_pending_check CHECK (
        (next_attempt_at IS NOT NULL) = (status = 'STARTING' OR (status = 'IN_PROGRESS' AND finish_requested_at IS NOT NULL)));

DROP INDEX ix_quality_control_pending;
CREATE INDEX ix_quality_control_pending ON quality_control (next_attempt_at, id) WHERE next_attempt_at IS NOT NULL;

-- ---- Fallas (US25 esc. 2, US26, US27, US28) ----
CREATE TABLE quality_failure (
    id                   UUID PRIMARY KEY,
    -- el ÚLTIMO control que la originó o reabrió; las pruebas de los anteriores siguen ligadas a su propio control
    control_id           UUID           NOT NULL REFERENCES quality_control (id),
    batch_id             UUID           NOT NULL,
    company_id           UUID           NOT NULL,
    root_cause           TEXT,
    affected_qty_kg      NUMERIC(14, 4),
    observations         TEXT,
    status               VARCHAR(20)    NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL,
    -- descarte pendiente: DISCARD sin respuesta de Production; la falla sigue OPEN hasta que confirme
    discard_requested_at TIMESTAMPTZ,
    discard_requested_by UUID,
    attempts             INT            NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMPTZ,
    last_error           TEXT,
    CONSTRAINT quality_failure_status_check CHECK (status IN ('OPEN', 'CONFIRMED', 'RESOLVED')),
    CONSTRAINT quality_failure_qty_check CHECK (affected_qty_kg IS NULL OR affected_qty_kg > 0),
    CONSTRAINT quality_failure_discard_columns_check CHECK ((discard_requested_at IS NULL) = (discard_requested_by IS NULL)),
    CONSTRAINT quality_failure_confirmed_check CHECK (status <> 'CONFIRMED' OR (discard_requested_at IS NOT NULL AND root_cause IS NOT NULL
        AND affected_qty_kg IS NOT NULL)),
    CONSTRAINT quality_failure_pending_check CHECK ((next_attempt_at IS NOT NULL) = (status = 'OPEN' AND discard_requested_at IS NOT NULL))
);
-- Un lote tiene a lo sumo una falla abierta
CREATE UNIQUE INDEX ux_quality_failure_open ON quality_failure (batch_id) WHERE status = 'OPEN';
CREATE INDEX ix_quality_failure_batch ON quality_failure (batch_id, created_at);
CREATE INDEX ix_quality_failure_pending ON quality_failure (next_attempt_at, id) WHERE next_attempt_at IS NOT NULL;

-- ---- Avisos de posible falla derivada (PossibleDerivedFailureDetected, US34) ----
-- impact_type se deduce de commonAncestorBatchId (no hay columna); next_attempt_at solo mientras el aviso está PENDING:
-- cuándo volver a decidir (ahora si hay que mandar el HOLD; más tarde si se espera a que el lote vuelva a AVAILABLE).
CREATE TABLE impacted_batch (
    failure_id      UUID         NOT NULL,
    batch_id        UUID         NOT NULL,
    impact_type     VARCHAR(20)  NOT NULL,
    lock_status     VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error      TEXT,
    PRIMARY KEY (failure_id, batch_id),
    CONSTRAINT impacted_batch_impact_check CHECK (impact_type IN ('ANCESTOR', 'DESCENDANT', 'RELATED_BRANCH')),
    CONSTRAINT impacted_batch_lock_check CHECK (lock_status IN ('PENDING', 'APPLIED', 'NO_ACTION')),
    CONSTRAINT impacted_batch_pending_check CHECK ((next_attempt_at IS NOT NULL) = (lock_status = 'PENDING'))
);
CREATE INDEX ix_impacted_batch_pending ON impacted_batch (next_attempt_at, failure_id, batch_id) WHERE next_attempt_at IS NOT NULL;
CREATE INDEX ix_impacted_batch_batch ON impacted_batch (batch_id) WHERE lock_status = 'PENDING';

-- ---- Outbox (ADR-004): los eventos se guardan en la misma transacción que el cambio ----
CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(80)  NOT NULL,
    aggregate_id   VARCHAR(80)  NOT NULL,
    event_type     VARCHAR(80)  NOT NULL,
    topic          VARCHAR(120) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED')),
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL,   -- lo escribe la aplicación (OutboxEventRecorder), no now()
    published_at   TIMESTAMPTZ
);
CREATE INDEX ix_outbox_pending ON outbox (status, created_at);
