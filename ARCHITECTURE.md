# Audit Log Service Architecture

## Overview
Design of a Spring Boot-based tamper-evident audit log service with PostgreSQL persistence, SHA-256 hash chaining, append-only storage, verification APIs, and paginated event queries.

Goals:
- Persist append-only audit records.
- Maintain a tamper-evident record chain using SHA-256.
- Expose a verification API for single-record and chain validation.
- Provide event query APIs with filtering and pagination.
- Keep the implementation aligned to standard audit event fields: `eventType`, `actorId`, `resourceType`, `resourceId`, `payload`, `timestamp`.

---

## Architecture

### High-level layers
1. API Layer
   - Spring MVC REST controllers.
   - REST endpoints for ingest, query, and verification.
2. Service Layer
   - Business rules for audit creation, chain hash computation, and verification.
3. Persistence Layer
   - Spring Data JPA repository for PostgreSQL.
   - Append-only event storage.
4. Hashing / Verification Layer
   - SHA-256 chain computation.
   - Integrity validation logic.
5. Security & Operations
   - Authentication, authorization, and audit-level access control.
   - Database policies to prevent updates and deletes.

### Component diagram

- `AuditEventController`
  - `POST /api/audit/events`
  - `GET /api/audit/events`
  - `GET /api/audit/events/{eventId}`
  - `GET /api/audit/events/{eventId}/verify`
  - `GET /api/audit/events/verify-chain`
- `AuditEventService`
  - `createEvent(CreateAuditEventRequest)`
  - `queryEvents(...)`
  - `getEvent(UUID)`
  - `verifyEvent(UUID)`
  - `verifyChain(...)`
- `AuditEventRepository`
  - `save(AuditEvent)`
  - `findById(UUID)`
  - `findByFilters(...)`
  - `findTopByOrderByCreatedAtDesc()`
- `HashChainService`
  - `computeRecordHash(auditEvent, previousHash)`
  - `recomputeHash(record)`
  - `validateRecord(record, previousRecord)`
- `AuditEvent` entity
  - persisted in `audit_event` table.

---

## Components

### API Layer

#### `AuditEventController`
Responsibilities:
- Accept write requests for new audit events.
- Provide event query endpoints with filters and pagination.
- Provide verification endpoints for individual events and chain integrity.

Endpoints:
- `POST /api/audit/events` — create audit event.
- `GET /api/audit/events` — search and page events.
- `GET /api/audit/events/{eventId}` — retrieve a single event.
- `GET /api/audit/events/{eventId}/verify` — verify an event hash.
- `GET /api/audit/events/verify-chain` — verify a chain segment.

### Service Layer

#### `AuditEventService`
Responsibilities:
- Validate incoming payloads and required fields.
- Determine the latest chain tail hash.
- Compute new record hash using SHA-256.
- Persist events in an append-only manner.
- Execute filtered event queries with pagination.
- Run hash integrity and chain validation.

Behavior:
- On create:
  - Calculate `previousHash` from latest event or genesis constant.
  - Compute `recordHash` from canonical event fields and `previousHash`.
  - Save the event in one transaction.
- On verification:
  - Recompute the event hash and compare against persisted hash.
  - Confirm `previousHash` matches the predecessor record hash.

### Persistence Layer

#### `AuditEventRepository`
- Persist audit events into PostgreSQL.
- Support filter queries by event fields and date range.
- Support paginated retrieval using `Pageable`.
- Retrieve the latest event to continue the hash chain.

### Hashing / Verification Layer

#### `HashChainService`
Responsibilities:
- Compute SHA-256 digest for every event record.
- Validate single event integrity.
- Validate a segment or full event chain.

Hash algorithm:
- Canonical input string:
  `timestamp|eventType|actorId|resourceType|resourceId|payload|previousHash`
- Compute digest: `SHA256(canonicalInput)`
- Store output as lowercase hex string.

Verification steps:
- Recompute hash from persisted event fields.
- Compare recomputed value to `recordHash`.
- For chained records, verify `previousHash` equals predecessor `recordHash`.
- Return a verification report with any mismatch details.

### Security & Operations

Responsibilities:
- Enforce append-only behavior by disallowing updates and deletes from the service API.
- Restrict database access to the service role for insert/select only.
- Protect APIs with authentication (JWT/OAuth2) and TLS.
- Enable monitoring, logging, and audit trail retention policies.

Operational controls:
- Database trigger to block `UPDATE`/`DELETE` on `audit_event`.
- Separate read-only role for query/reporting.
- Backup and retention strategy for PostgreSQL.

---

## Database design

### Table: `audit_event`

Columns:
- `id` UUID PRIMARY KEY DEFAULT gen_random_uuid()
- `created_at` TIMESTAMP WITH TIME ZONE NOT NULL
- `event_type` VARCHAR(128) NOT NULL
- `actor_id` VARCHAR(256) NOT NULL
- `resource_type` VARCHAR(128) NOT NULL
- `resource_id` VARCHAR(256) NOT NULL
- `payload` JSONB NOT NULL
- `timestamp` TIMESTAMP WITH TIME ZONE NOT NULL
- `previous_hash` CHAR(64) NOT NULL
- `record_hash` CHAR(64) NOT NULL
- `metadata` JSONB NULL
- `version` BIGINT NOT NULL DEFAULT 0

Field reference:

| Field | Type | Description | Required |
|---|---|---|---|
| `id` | `UUID` | Unique audit event identifier | Yes |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | Ingest timestamp assigned by service | Yes |
| `event_type` | `VARCHAR(128)` | Business event type | Yes |
| `actor_id` | `VARCHAR(256)` | Actor who caused the event | Yes |
| `resource_type` | `VARCHAR(128)` | Type of resource affected | Yes |
| `resource_id` | `VARCHAR(256)` | Identifier of affected resource | Yes |
| `payload` | `JSONB` | Structured event details | Yes |
| `timestamp` | `TIMESTAMP WITH TIME ZONE` | Event occurrence time | Yes |
| `previous_hash` | `CHAR(64)` | SHA-256 hash of previous record | Yes |
| `record_hash` | `CHAR(64)` | SHA-256 hash for this record | Yes |
| `metadata` | `JSONB` | Optional tracing or context | No |
| `version` | `BIGINT` | Optimistic concurrency / schema evolution | Yes |

Indexes:
- `idx_audit_event_created_at` ON (`created_at`)
- `idx_audit_event_event_type` ON (`event_type`)
- `idx_audit_event_actor_id` ON (`actor_id`)
- `idx_audit_event_resource_type` ON (`resource_type`)
- `idx_audit_event_resource_id` ON (`resource_id`)

Constraints:
- Do not allow `UPDATE` or `DELETE` in regular operations.
- Maintain chain integrity by computing hash values in the application.

Append-only enforcement example:
```sql
CREATE FUNCTION audit_event_prevent_modification() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'Audit records are append-only and cannot be modified or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_no_mutation
BEFORE UPDATE OR DELETE ON audit_event
FOR EACH ROW EXECUTE FUNCTION audit_event_prevent_modification();
```

Genesis chain initialization:
- Use a fixed genesis hash constant for the first record, such as `000...000`.

---

## API design

### POST /api/audit/events
Create a new audit event.

Request:
```json
{
  "eventType": "RECORD_UPDATED",
  "actorId": "user-123",
  "resourceType": "ACCOUNT",
  "resourceId": "account-456",
  "payload": {
    "field": "status",
    "oldValue": "inactive",
    "newValue": "active"
  },
  "timestamp": "2026-08-12T15:30:00Z",
  "metadata": {
    "correlationId": "req-789"
  }
}
```

Response:
- `201 Created`
- `Location: /api/audit/events/{id}`
- Body:
```json
{
  "id": "uuid",
  "createdAt": "2026-08-12T15:30:00Z",
  "eventType": "RECORD_UPDATED",
  "actorId": "user-123",
  "resourceType": "ACCOUNT",
  "resourceId": "account-456",
  "payload": { ... },
  "timestamp": "2026-08-12T15:30:00Z",
  "previousHash": "000...000",
  "recordHash": "abcdef...",
  "version": 0
}
```

### GET /api/audit/events
Query audit events with filters and pagination.

Query parameters:
- `eventType` (optional)
- `actorId` (optional)
- `resourceType` (optional)
- `resourceId` (optional)
- `from` (optional ISO-8601 timestamp)
- `to` (optional ISO-8601 timestamp)
- `page` (optional, default 0)
- `size` (optional, default 20)
- `sort` (optional, default `createdAt,desc`)

Response:
- `200 OK`
- Headers:
  - `X-Total-Count`
  - `X-Total-Pages`
  - `X-Page-Number`
  - `X-Page-Size`
- Body:
```json
{
  "page": 0,
  "size": 20,
  "totalElements": 1234,
  "totalPages": 62,
  "events": [
    {
      "id": "uuid",
      "createdAt": "2026-08-12T15:30:00Z",
      "eventType": "RECORD_UPDATED",
      "actorId": "user-123",
      "resourceType": "ACCOUNT",
      "resourceId": "account-456",
      "payload": { ... },
      "timestamp": "2026-08-12T15:30:00Z",
      "previousHash": "...",
      "recordHash": "..."
    }
  ]
}
```

### GET /api/audit/events/{eventId}
Retrieve a single event by ID.

Response:
- `200 OK`
- Body includes all persisted fields and hash chain values.

### GET /api/audit/events/{eventId}/verify
Verify a single event.

Response:
- `200 OK`
- Body:
```json
{
  "eventId": "uuid",
  "isValid": true,
  "expectedHash": "abcdef...",
  "actualHash": "abcdef...",
  "previousHashMatches": true,
  "message": "Event integrity verified"
}
```

### GET /api/audit/events/verify-chain
Verify chain integrity for a range of events.

Query parameters:
- `fromId` (optional)
- `toId` (optional)
- `limit` (optional, default 1000)

Response:
- `200 OK`
- Body:
```json
{
  "chainVerified": true,
  "checkedCount": 100,
  "firstVerifiedId": "uuid",
  "lastVerifiedId": "uuid",
  "errors": []
}
```

If mismatch found:
```json
{
  "chainVerified": false,
  "checkedCount": 45,
  "errors": [
    {
      "eventId": "uuid",
      "problem": "Record hash mismatch",
      "expectedHash": "...",
      "actualHash": "..."
    }
  ]
}
```

---

## Verification workflow

1. Load the requested record or chain segment in creation order.
2. For each record:
   - Build the canonical input from `timestamp`, `eventType`, `actorId`, `resourceType`, `resourceId`, `payload`, and `previousHash`.
   - Compute SHA-256 and compare to `recordHash`.
   - Confirm `previousHash` matches the prior record's `recordHash`.
3. Return a report with validation status and any discrepancies.

Notes:
- Use canonical JSON serialization for `payload`.
- Store timestamps in UTC.
- Use a fixed genesis hash constant for the first chain record.

---

## Deployment considerations

- Deploy the Spring Boot app behind an API gateway.
- Use connection pooling for PostgreSQL.
- Configure database backups and secure access.
- Use read-only replicas for analytic query load if needed.
- Apply API security and audit logging for the service.
