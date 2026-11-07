-- user_id nulo = acción del sistema (p. ej. un HOLD por una falla derivada, que dispara un evento y no una persona).
-- El comando interno de Quality (ADR-009) exige requestedBy en las demás operaciones.
ALTER TABLE audit_log ALTER COLUMN user_id DROP NOT NULL;
