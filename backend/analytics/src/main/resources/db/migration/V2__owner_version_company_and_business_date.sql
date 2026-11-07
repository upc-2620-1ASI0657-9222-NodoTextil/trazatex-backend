-- I9: la empresa dueña de un lote se decide por batchVersion (BatchPhaseChanged), no por el reloj de los eventos.
-- -1 = el dueño de nacimiento (BatchRegistered, BatchSplit, TransformationCompleted) y los TransferAccepted ya guardados por la versión anterior:
-- cualquier BatchPhaseChanged con versión los reemplaza.
ALTER TABLE batch_owner ADD COLUMN batch_version BIGINT NOT NULL DEFAULT -1;

-- M9: empresa y fecha del hecho como columnas, no como texto dentro del JSONB.
--   company_id  empresa que el indicador atribuye al hecho: la de origen en las transferencias (I8), la del payload en los demás
--   business_at cuándo ocurrió el hecho de negocio: completedAt / finishedAt / acceptedAt, o occurred_at si el evento no lo trae
ALTER TABLE analytics_event ADD COLUMN company_id UUID;
ALTER TABLE analytics_event ADD COLUMN business_at TIMESTAMPTZ;

CREATE FUNCTION analytics_to_uuid(value TEXT) RETURNS UUID LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
    RETURN CAST(value AS UUID);
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END $$;

CREATE FUNCTION analytics_to_ts(value TEXT) RETURNS TIMESTAMPTZ LANGUAGE plpgsql STABLE AS $$
BEGIN
    RETURN CAST(value AS TIMESTAMPTZ);
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END $$;

UPDATE analytics_event SET
    company_id = analytics_to_uuid(CASE WHEN event_type IN ('TransferInitiated', 'TransferAccepted', 'TransferRejected')
                                        THEN payload->>'fromCompanyId' ELSE payload->>'companyId' END),
    business_at = coalesce(CASE event_type
                               WHEN 'TransformationCompleted' THEN analytics_to_ts(payload->>'completedAt')
                               WHEN 'QualityControlCompleted' THEN analytics_to_ts(payload->>'finishedAt')
                               WHEN 'TransferAccepted' THEN analytics_to_ts(payload->>'acceptedAt')
                           END, occurred_at);

-- Los BatchPhaseChanged ya guardados traen la versión: el dueño sale de ellos.
INSERT INTO batch_owner(event_id, batch_id, company_id, occurred_at, batch_version)
SELECT event_id, analytics_to_uuid(payload->>'batchId'), analytics_to_uuid(payload->>'companyId'), occurred_at, CAST(payload->>'batchVersion' AS BIGINT)
FROM analytics_event
WHERE event_type = 'BatchPhaseChanged' AND analytics_to_uuid(payload->>'batchId') IS NOT NULL
  AND analytics_to_uuid(payload->>'companyId') IS NOT NULL AND payload->>'batchVersion' ~ '^[0-9]{1,18}$'
ON CONFLICT DO NOTHING;

DROP FUNCTION analytics_to_uuid(TEXT);
DROP FUNCTION analytics_to_ts(TEXT);

ALTER TABLE analytics_event ALTER COLUMN business_at SET NOT NULL;

DROP INDEX analytics_event_type_time;
CREATE INDEX analytics_event_company_type_time ON analytics_event(company_id, event_type, business_at);
DROP INDEX batch_owner_time;
CREATE INDEX batch_owner_version ON batch_owner(batch_id, batch_version DESC, occurred_at DESC, event_id DESC);
CREATE INDEX batch_birth_registered ON batch_birth(registered_at);
