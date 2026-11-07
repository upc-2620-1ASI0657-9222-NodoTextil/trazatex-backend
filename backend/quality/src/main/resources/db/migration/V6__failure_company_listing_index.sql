-- GET /api/quality/failures pagina por empresa, de la más reciente a la más antigua (I12): este índice sirve ese orden sin ordenar.
CREATE INDEX ix_quality_failure_company ON quality_failure (company_id, created_at DESC, id);
