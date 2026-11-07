CREATE TABLE analytics_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    aggregate_id TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    projected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX analytics_event_type_time ON analytics_event(event_type,occurred_at);
CREATE INDEX analytics_event_aggregate ON analytics_event(aggregate_id,occurred_at);
CREATE TABLE batch_birth (
    batch_id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE batch_owner (
    event_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    company_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(event_id,batch_id)
);
CREATE INDEX batch_owner_time ON batch_owner(batch_id,occurred_at DESC);
CREATE TABLE batch_quality (
    event_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(event_id,batch_id)
);
CREATE INDEX batch_quality_time ON batch_quality(batch_id,occurred_at DESC);

