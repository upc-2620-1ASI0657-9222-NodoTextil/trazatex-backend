-- RNF-03: la búsqueda de lotes por prefijo del Traceability ID (US13) es `traceability_id LIKE 'TT-AB%'` dentro de la
-- empresa. El UNIQUE de V1 usa el operador por defecto, que con la configuración regional de la base (en_US.UTF-8, no "C")
-- NO sirve para LIKE 'prefijo%'. `text_pattern_ops` compara byte a byte y sí: la consulta usa este índice (comprobado con
-- EXPLAIN en BatchSearchPerformanceIT). Va con company_id primero porque la búsqueda siempre es de una sola empresa.
CREATE INDEX ix_batches_company_traceability_prefix ON batches (company_id, traceability_id text_pattern_ops);
