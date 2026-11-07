ALTER TABLE deferred_email ADD COLUMN expires_at TIMESTAMPTZ;
UPDATE deferred_email SET expires_at=substring(body FROM '([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:.]+Z)')::timestamptz
WHERE subject='Invitación a TrazaTex';

