-- PostgreSQL schema for audit log service with SHA-256 hash chaining

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS audit_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    actor_id VARCHAR(256) NOT NULL,
    resource_type VARCHAR(128) NOT NULL,
    resource_id VARCHAR(256) NOT NULL,
    payload JSONB NOT NULL,
    previous_hash CHAR(64) NOT NULL,
    record_hash CHAR(64) NOT NULL,
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_audit_event_created_at ON audit_event (created_at);
CREATE INDEX IF NOT EXISTS idx_audit_event_event_type ON audit_event (event_type);
CREATE INDEX IF NOT EXISTS idx_audit_event_actor_id ON audit_event (actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_resource_type ON audit_event (resource_type);
CREATE INDEX IF NOT EXISTS idx_audit_event_resource_id ON audit_event (resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_event_timestamp ON audit_event (event_timestamp);

CREATE FUNCTION audit_event_prevent_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Audit records are append-only and cannot be modified or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_no_mutation
BEFORE UPDATE OR DELETE ON audit_event
FOR EACH ROW EXECUTE FUNCTION audit_event_prevent_modification();
