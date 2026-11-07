-- 06d: el Traceability ID del lote en la proyección (lo identifica en el reporte PDF, que se comparte) y las evidencias fotográficas (US43, ADR-007).

-- Los eventos que dan a conocer un lote (BatchRegistered, BatchSplit, TransformationCompleted) traen su Traceability ID. Las filas anteriores a esta
-- migración quedan sin él (el reporte muestra entonces el UUID del lote) y lo completan si el evento de nacimiento se vuelve a procesar.
ALTER TABLE batch_projection ADD COLUMN traceability_id VARCHAR(13);

-- Evidencias de un control o de una falla. La foto se sube a Cloudinary durante la petición; si Cloudinary falla (timeout, 5xx, circuito abierto, sin
-- configurar) la evidencia queda PENDING y sus bytes en evidence_upload_queue (PostgreSQL, nunca el disco del contenedor) hasta que un job la sube.
-- Sin clave foránea al dueño: es polimórfico (control o falla).
CREATE TABLE evidence (
    id                UUID PRIMARY KEY,                      -- también es el public_id en Cloudinary
    owner_type        VARCHAR(20)  NOT NULL CHECK (owner_type IN ('CONTROL', 'FAILURE')),
    owner_id          UUID         NOT NULL,
    company_id        UUID         NOT NULL,                 -- la empresa que la subió: cada empresa ve solo las suyas
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'UPLOADED', 'FAILED')),
    url               TEXT,
    original_filename VARCHAR(255),
    content_type      VARCHAR(30)  NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    size_bytes        BIGINT       NOT NULL CHECK (size_bytes > 0),
    uploaded_by       UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    uploaded_at       TIMESTAMPTZ,
    attempts          INT          NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at   TIMESTAMPTZ,
    last_error        TEXT,
    CONSTRAINT ck_evidence_uploaded CHECK ((status = 'UPLOADED') = (url IS NOT NULL AND uploaded_at IS NOT NULL)),
    CONSTRAINT ck_evidence_pending_schedule CHECK (status <> 'PENDING' OR next_attempt_at IS NOT NULL)
);
CREATE INDEX ix_evidence_owner ON evidence (owner_type, owner_id, company_id, created_at, id);
CREATE INDEX ix_evidence_due ON evidence (next_attempt_at) WHERE status = 'PENDING';

-- Los bytes de lo que aún no se subió. Se borra la fila al marcar UPLOADED; en FAILED se conserva.
CREATE TABLE evidence_upload_queue (
    evidence_id UUID PRIMARY KEY REFERENCES evidence (id),
    content     BYTEA NOT NULL
);
