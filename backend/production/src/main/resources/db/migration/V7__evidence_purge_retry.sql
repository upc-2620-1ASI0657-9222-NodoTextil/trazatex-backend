-- La purga de una evidencia que falla no frena a las demás: la fila se aplaza con espera creciente (10 s, 20 s, 40 s ... tope 5 min).
ALTER TABLE evidence ADD COLUMN purge_attempts INT NOT NULL DEFAULT 0, ADD COLUMN purge_retry_at TIMESTAMPTZ;
