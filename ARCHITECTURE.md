# Audit Log Service Architecture

## Overview
This project implements a Spring Boot-based tamper-evident audit log service using JPA and H2 for local development. The service stores audit events in a hash-linked chain, validates integrity through SHA-256, and exposes APIs for creation, search, verification, redaction, archival, and export.

The implementation is designed to remain adaptable for production-grade persistence while currently using H2 for fast development and local validation.

Goals:
- Persist audit events in an append-friendly model.
- Maintain tamper-evident integrity using SHA-256 hash chaining.
- Expose verification APIs for the chain state.
- Support search, archive, redaction, and export operations.
- Keep the domain model aligned with the actual event fields used by the service: eventType, actorId, resourceType, resourceId, payload, payloadHash, currentHash, previousHash, and archived.

---

## Architecture

### High-level layers
1. API Layer
   - Spring MVC REST controller for audit operations.
   - Endpoints under the /audit base path.
2. Service Layer
   - Business logic for event creation, verification, redaction, archival, and export.
3. Persistence Layer
   - Spring Data JPA repository with H2 in-memory database by default.
   - JPA abstraction supports future migration to another relational database.
4. Hashing / Integrity Layer
   - SHA-256 hash generation and verification logic in HashService.
5. Retention / Operations Layer
   - Scheduled retention policy using Spring Scheduling.
   - Manual archival trigger for expired events.

### Component diagram

- AuditEventController
  - POST /audit/events
  - GET /audit/events
  - GET /audit/verify
  - POST /audit/events/{id}/redact
  - POST /audit/events/{id}/archive
  - POST /audit/retention/archive
  - GET /audit/exports/actor/{actorId}
  - GET /audit/exports/resource/{resourceId}
- AuditEventService
  - createEvent(CreateAuditEventRequest)
  - searchEvents(...)
  - verifyChain()
  - redactEvent(id, request)
  - archiveEvent(id)
  - archiveExpiredEvents()
  - exportByActor(actorId)
  - exportByResource(resourceId)
- AuditEventRepository
  - save(AuditEvent)
  - findById(id)
  - findAllByOrderByIdAsc()
  - findAllByActorIdAndArchivedFalseOrderByIdAsc(actorId)
  - findAllByResourceIdAndArchivedFalseOrderByIdAsc(resourceId)
  - findAllByArchivedFalseAndEventTimestampBeforeOrderByIdAsc(cutoff)
- HashService
  - calculateHash(AuditEvent)
  - calculatePayloadHash(String)
  - sha256(String)
- RetentionPolicy
  - calculateCutoff(Instant)
- AuditEventSpecification
  - filter criteria for search requests
- AuditEvent entity
  - persisted as audit_events in the database

---

## Components

### API Layer

#### AuditEventController
Responsibilities:
- Accept event creation requests.
- Support searching/filtering by actor, resource, type, and time window.
- Provide chain verification.
- Provide redaction, archival, and export endpoints.

Endpoints:
- POST /audit/events — create a new audit event.
- GET /audit/events — search and filter events using query parameters.
- GET /audit/verify — verify the full hash chain.
- POST /audit/events/{id}/redact — redact selected payload fields.
- POST /audit/events/{id}/archive — archive a specific event.
- POST /audit/retention/archive — manually archive expired events.
- GET /audit/exports/actor/{actorId} — export non-archived events for a given actor.
- GET /audit/exports/resource/{resourceId} — export non-archived events for a given resource.

### Service Layer

#### AuditEventService
Responsibilities:
- Validate request payloads and generate event hashes.
- Compute the previous hash from the most recent record in the chain.
- Persist audit events and keep verification semantics intact.
- Support redaction without altering the hash chain.
- Archive expired records based on retention policy.
- Export non-archived records in a bundle with a bundle hash.

Behavior:
- On create:
  - calculate payloadHash from JSON payload
  - obtain the latest currentHash as previousHash or use GENESIS
  - compute the record hash from event data, payloadHash, timestamp, and previousHash
  - save the event
- On verification:
  - walk the records in ID order
  - confirm previousHash matches the expected predecessor
  - recompute currentHash and compare with the stored value
- On archival:
  - set archived = true
  - keep hash values unchanged
  - exclude archived events from export results

### Persistence Layer

#### AuditEventRepository
Responsibilities:
- Store audit events in a relational database through JPA.
- Support event search and filtering by actor, resource, type, and date range.
- Query only non-archived records for export and retention processing.

### Hashing / Verification Layer

#### HashService
Responsibilities:
- Compute SHA-256 digests for payloads and event records.
- Validate integrity of the full chain.

Hash algorithm:
- Payload hash: SHA-256(payloadJson)
- Event hash canonical input:
  eventType|actorId|resourceType|resourceId|payloadHash|eventTimestamp|previousHash
- store the result in currentHash

Verification steps:
- read records in order by id
- validate previousHash against the expected predecessor hash
- recompute currentHash from persisted fields
- compare with stored value
- return a verification result with mismatch details when needed

### Retention / Compliance Layer

#### RetentionPolicy
Responsibilities:
- determine the cutoff point for expired events based on retention configuration
- help scheduled and manual retention processing archive outdated records

Operational behavior:
- retention is evaluated using eventTimestamp
- only non-archived events are considered
- archived events remain in the data history but are excluded from export sets

---

## Database design

### Table: `audit_events`

The actual persistence model used by the service is a single table named audit_events with the following fields:

| Field | Type | Description | Required |
|---|---|---|---|
| `id` | `BIGINT` | Auto-generated database primary key | Yes |
| `event_type` | `VARCHAR(255)` | Event category or business action | Yes |
| `actor_id` | `VARCHAR(255)` | User or system actor | Yes |
| `resource_type` | `VARCHAR(255)` | Resource category | Yes |
| `resource_id` | `VARCHAR(255)` | Target resource identifier | Yes |
| `payload` | `TEXT` | JSON payload content | Yes |
| `payload_hash` | `VARCHAR(255)` | SHA-256 hash of the payload JSON | Yes |
| `redacted_fields` | `TEXT` | Comma-separated list of fields that were redacted | No |
| `event_timestamp` | `TIMESTAMP` | When the event occurred | Yes |
| `current_hash` | `VARCHAR(255)` | Current hash for chain integrity | Yes |
| `previous_hash` | `VARCHAR(255)` | Hash of the preceding record | Yes |
| `created_at` | `TIMESTAMP` | When the record was created by the service | Yes |
| `archived` | `BOOLEAN` | Indicates whether the record is considered archived | No |

Notes:
- The current implementation uses H2 in-memory storage with schema generation via JPA.
- The model is intentionally simple and append-oriented for local development and validation.
- Archived events remain stored but are excluded from active exports.

---

## API design

### POST /audit/events
Create a new audit event.

Request example:
```json
{
  "eventType": "USER_LOGIN",
  "actorId": "user-101",
  "resourceType": "ACCOUNT",
  "resourceId": "ACC-001",
  "payload": {
    "ipAddress": "10.10.10.10",
    "status": "SUCCESS"
  },
  "timestamp": "2026-08-11T10:00:00Z"
}
```

Response:
- `200 OK`
- Body is an `AuditEventResponse` containing the created event plus hash data.

### GET /audit/events
Search events by filter and pagination.

Query parameters:
- `actorId` (optional)
- `resourceType` (optional)
- `resourceId` (optional)
- `eventType` (optional)
- `from` (optional ISO-8601 timestamp)
- `to` (optional ISO-8601 timestamp)
- `page` (optional)
- `size` (optional)
- `sort` (optional)

Response:
- `200 OK`
- Spring Page payload containing event responses and metadata.

### GET /audit/verify
Validate the full hash chain.

Response:
- `200 OK`
- Returns a VerifyResponse with a boolean valid flag, mismatch location, and message.

### POST /audit/events/{id}/redact
Redact selected fields in the payload.

Request example:
```json
{
  "fields": ["ipAddress", "ssn"]
}
```

Response:
- `200 OK`
- Updated event response with the redacted field list.

### POST /audit/events/{id}/archive
Archive a single event.

Response:
- `200 OK`
- Updated event response with archived = true.

### POST /audit/retention/archive
Manually archive expired events.

Response:
- `200 OK`
- Returns the count of archived records.

### GET /audit/exports/actor/{actorId}
Export non-archived events for a specific actor.

### GET /audit/exports/resource/{resourceId}
Export non-archived events for a specific resource.

---

## Verification workflow

1. Read the event records in ascending ID order.
2. Validate that the previousHash matches the prior currentHash or the genesis value.
3. Recompute the currentHash using the persisted event fields and payloadHash.
4. Compare the recomputed value with the stored currentHash.
5. Return the verification status and any mismatch details.

Notes:
- Hashes are computed for integrity validation, not for data mutation.
- Archived records are excluded from export bundles but remain in the underlying audit history.
- The service stores payloadHash separately to make payload tampering detectable while preserving a clean event record model.

---

## Deployment considerations

- Keep the H2 configuration for local development and testing.
- Move to a production-grade relational database when required by scale or compliance needs.
- Use Spring Scheduling with retention policy configuration for periodic archival.
- Review access control and retention policies for operational compliance.
- Keep the hash verification path enabled whenever audit records are accessed or exported.
