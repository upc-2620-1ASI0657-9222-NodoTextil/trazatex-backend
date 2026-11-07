-- Proyección local de lotes (docs/domain/quality.md, "BatchProjection"): se alimenta solo de eventos de Production (nunca por REST).
-- quality_status es NULL mientras solo se conoce la fila por un BatchPhaseChanged que llegó antes que el evento que da a conocer el
-- lote (BatchRegistered, BatchSplit o TransformationCompleted viajan en otros tópicos: no hay orden entre ellos).
-- batch_version es la del último BatchPhaseChanged aplicado (-1: ninguno todavía); el evento solo se aplica si trae una mayor.
CREATE TABLE batch_projection (
    batch_id        UUID PRIMARY KEY,
    company_id      UUID        NOT NULL,
    phase           VARCHAR(30) NOT NULL,
    quality_status  VARCHAR(40),
    quality_hold    BOOLEAN     NOT NULL,
    ever_controlled BOOLEAN     NOT NULL DEFAULT FALSE,
    batch_version   BIGINT      NOT NULL DEFAULT -1,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT batch_projection_phase_check CHECK (phase IN
        ('AVAILABLE', 'IN_TRANSFORMATION', 'IN_TRANSFER', 'IN_QUALITY_CONTROL', 'PROCESSED', 'SPLIT', 'DISCARDED')),
    CONSTRAINT batch_projection_quality_status_check CHECK (quality_status IS NULL OR quality_status IN
        ('NOT_REVIEWED', 'CONFORMING', 'FAILED', 'PENDING_REEVALUATION', 'POTENTIAL_DERIVED_FAILURE'))
);

CREATE INDEX batch_projection_company ON batch_projection (company_id);

-- Eventos ya aplicados (ADR-004): el consumo es idempotente
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
