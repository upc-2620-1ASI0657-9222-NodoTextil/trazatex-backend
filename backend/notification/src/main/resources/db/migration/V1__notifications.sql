CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE user_projection (
    user_id UUID PRIMARY KEY,
    company_id UUID,
    role VARCHAR(30) NOT NULL,
    email TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE notification (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    recipient_user_id UUID,
    company_id UUID,
    recipient_role VARCHAR(30) NOT NULL,
    type VARCHAR(60) NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    resource_type VARCHAR(30),
    resource_id TEXT,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX notification_scope ON notification(event_id, coalesce(recipient_user_id::text, ''), coalesce(company_id::text, ''), recipient_role);
CREATE INDEX notification_company ON notification(company_id, recipient_role, created_at DESC);
CREATE TABLE notification_read (
    notification_id UUID NOT NULL REFERENCES notification(id),
    user_id UUID NOT NULL,
    read_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(notification_id, user_id)
);
CREATE TABLE deferred_email (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    recipient TEXT NOT NULL,
    subject TEXT NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(200),
    accepted_at TIMESTAMPTZ,
    UNIQUE(event_id, recipient)
);
CREATE INDEX deferred_email_due ON deferred_email(next_attempt_at) WHERE status = 'PENDING';

