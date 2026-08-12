-- H2 schema for audit log service (compatible with in-memory H2)

CREATE TABLE IF NOT EXISTS audit_event (
    id UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
    event_timestamp TIMESTAMP NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    actor_id VARCHAR(256) NOT NULL,
    resource_type VARCHAR(128) NOT NULL,
    resource_id VARCHAR(256) NOT NULL,
    payload CLOB NOT NULL,
    previous_hash CHAR(64) NOT NULL,
    record_hash CHAR(64) NOT NULL,
    metadata CLOB,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_audit_event_created_at ON audit_event (created_at);
CREATE INDEX IF NOT EXISTS idx_audit_event_event_timestamp ON audit_event (event_timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_event_event_type ON audit_event (event_type);
CREATE INDEX IF NOT EXISTS idx_audit_event_actor_id ON audit_event (actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_resource_type ON audit_event (resource_type);
CREATE INDEX IF NOT EXISTS idx_audit_event_resource_id ON audit_event (resource_id);

-- Note: H2 in-memory databases do not support PostgreSQL extensions like pgcrypto.
-- The append-only protection trigger (used in production PostgreSQL) is omitted
-- for H2 development mode; enforce append-only semantics at the application level.
