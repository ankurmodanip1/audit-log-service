# Scenario C — Compliance Reporting

## Clarified requirement statement

"The system must provide a tamper-evident, queryable record of access to client account data so a regulator can verify who accessed which account, when the access occurred, and whether the access was authorized. The audit trail must preserve integrity, support redaction of sensitive fields, and remain searchable and exportable within the service's retention policy."

## Ambiguities identified

The original requirement was intentionally under-specified. The main ambiguities were:

- What counts as access to client account data?
  - Read-only access, updates, exports, or any account-related action?
- Which account fields are considered sensitive?
  - Account number, SSN, PII, or all payload contents?
- How much context is required in each event?
  - Only actor/resource/time, or also purpose, authorization decision, and outcome?
- What is the reporting scope?
  - A single resource, a single actor, or a time-bounded compliance review?
- What is the retention expectation?
  - Should the service retain all access records, or archive them after a policy window?

## Assumptions and questions we would ask before production rollout

To normalize the requirement, we assumed the following:

1. Access events are represented as ordinary audit events using the existing event model.
2. `eventType` distinguishes access actions such as `ACCOUNT_ACCESS_VIEWED`, `ACCOUNT_ACCESS_UPDATED`, or `ACCOUNT_DATA_EXPORTED`.
3. `resourceType` is `ACCOUNT` and `resourceId` identifies the target client account.
4. Sensitive payload fields are redacted to satisfy privacy requirements without breaking the hash chain.
5. Compliance review is satisfied via the existing query, verification, and export APIs rather than a separate regulator portal.

If this were going to production, the next questions would be:

- Do we need explicit authorization metadata in every event?
- Which fields are classified as sensitive for each product line?
- Should exports be signed or time-stamped for legal evidence?
- Is there a retention period mandated by policy or regulation?

## Technical design

The implementation uses the same core audit model for compliance scenarios:

- `eventType`: identifies the type of access or action
- `actorId`: identifies who performed the action
- `resourceType`: the target domain object, such as `ACCOUNT`
- `resourceId`: the specific account affected
- `payload`: includes event-specific detail, such as access purpose, source system, or outcome
- `eventTimestamp`: records time of the access event
- `payloadHash` and `currentHash`: preserve tamper evidence
- `redactedFields`: tracks privacy-sensitive payload fields that were intentionally masked

This design is intentionally aligned to the existing service: compliant access events are not a separate subsystem; they are just the same append-only audit stream with more specific event metadata and filtering.

## What was implemented

The current service supports the compliance-reporting use case by providing:

- event creation with structured payloads
- filterable query by actor, resource, type, and time range
- verification of the full append-only hash chain
- redaction of sensitive payload values while preserving chain integrity through the established service design
- archival based on retention policy
- export of non-archived records by actor or resource for evidence bundles

## What was intentionally scoped out

The following items were not implemented because they exceed the scope of this prototype:

- a regulator-facing portal or dashboard
- role-based approval workflows or sign-off states
- a full compliance policy engine with obligations and legal hold rules
- multi-tenant or cross-system identity federation
- real-time streaming or event bus integration

These were omitted to keep the prototype focused on a working, testable audit trail and tamper-evidence model while preserving the ability to extend the service later.

## Trade-offs and limitations

The design favors simplicity and audit integrity over feature breadth:

- Simpler data model: easier to validate and explain in a short engineering exercise.
- Tamper-evidence remains strong because every record is chained to the previous hash.
- Redaction is handled by masked payload values plus tracked field metadata, which preserves privacy but does not attempt to rebuild a historical original record.
- The service is designed as a prototype rather than as a full regulator compliance platform.

This means the system is suitable for engineering validation and demonstration, but additional policy controls would be required before it could be treated as a production-ready compliance system.
