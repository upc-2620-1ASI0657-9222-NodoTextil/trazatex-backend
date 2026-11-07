ALTER TABLE evidence ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX ix_evidence_deleted ON evidence(deleted_at) WHERE deleted_at IS NOT NULL;
