# AI Usage Log

This log records the prompts given to the AI, the output produced, what was accepted or rejected, and what was modified or discarded during the development of the audit log service. The intent is to create a clear and auditable history of AI-assisted decision-making for the project.

## Prompt History and Decision Log

### 1. Initial architecture design

**Prompt given to AI**

> Act as a senior solution architect. Design a Spring Boot-based tamper-evident audit log service.

**AI output**

- Recommended layered architecture with Controller, Service, Repository, and persistence layer.
- Suggested H2 for local development and PostgreSQL for production.
- Proposed SHA-256 hash chaining for tamper-evident event records.
- Recommended a design aligned to append-only audit events and verification APIs.

**Accepted**

- Package structure
- Controller design
- Entity model
- Repository layer
- SHA-256 hash chain approach

**Modified / discarded**

- Replaced PostgreSQL-first design with H2 for initial local implementation and testing.
- This was a deliberate modification to reduce setup complexity and speed up iteration during early development.

**Reason for modification**

H2 allowed faster local development and reduced operational setup overhead. The architecture remains database-agnostic and can be switched to PostgreSQL later with minimal changes.

**Rejected**

- No major design rejection at this stage.

**Final note**

The architecture aligned well with the requirements and was retained with the local H2 adjustment.

### 2. Scenario A baseline implementation

**Prompt given to AI**

> Generate the initial implementation for the audit log service and build the baseline Scenario A features: event creation, search, and hash verification.

**AI output**

- Spring Boot app bootstrap
- Audit event entity and repository design
- Service layer for create/search/verify logic
- Hash and verification logic using previous/current hash chaining
- H2 schema and initial configuration
- Initial test classes for controller and service behavior

**Accepted**

- Entity structure and append-only event model
- SHA-256 verification flow
- Search and verification endpoints
- Initial H2-based configuration for local development

**Modified / discarded**

- The database choice was kept as H2 for local dev instead of PostgreSQL for the initial working version.
- Some implementation details were refined to match the actual project structure and test needs.

**Reason for modification**

The project needed rapid local validation and low setup cost before production-like configuration work.

**Rejected**

- No major rejection; only minor refinements were required to align with the actual domain model and working code.

**Result**

The baseline implementation successfully provided the core service behavior needed for the audit log workflow.

### 3. Scenario B restoration and feature completion

**Prompt given to AI**

> Restore and complete the Scenario B requirements: redaction, export bundles, and archive endpoints while preserving tamper-evident behavior.

**AI output**

- Redaction support for selected payload fields
- Archive endpoint logic
- Export-by-actor and export-by-resource functionality
- Integrity-preserving archive behavior without mutating the hash chain

**Accepted**

- Redaction implementation
- Archive endpoint concept and behavior
- Export bundle design
- Hash-chain preservation during archival

**Modified / discarded**

- The archive mechanism was adjusted so archived records remain part of the stored event history but are excluded from export bundles.
- The response DTO was adjusted with a backward-compatible constructor to reduce test churn and preserve older call sites.

**Reason for modification**

The main goal was to restore the required functionality without breaking verification semantics or existing tests.

**Rejected**

- No full feature rejection.
- Some earlier implementation ideas were refined to ensure the export behavior and archival logic stayed consistent with the tamper-evident model.

**Result**

The scenario was successfully restored and validated with the relevant test suite.

### 4. Scenario C retention policy and scheduled archival

**Prompt given to AI**

> Implement the retention requirement for Scenario C, including a scheduled archival process and a manual trigger for expired events.

**AI output**

- Retention policy logic for cutoff calculation
- Scheduled archival processing using Spring scheduling
- Manual `POST /audit/retention/archive` endpoint
- Archive logic based on `eventTimestamp` and archived flag

**Accepted**

- Retention-based archival design
- Manual retention trigger
- Scheduled archival flow
- Logic to exclude archived rows from active exports

**Modified / discarded**

- The retention logic was aligned to the actual domain model and test expectations rather than a more generic policy design.
- Archival decisions were implemented while preserving existing hash semantics and without altering event hashes when records are archived.

**Reason for modification**

Retention should not compromise audit integrity or generate false tampering results. The implementation therefore focused on metadata archiving rather than hash mutation.

**Rejected**

- No major rejection; some design refinements were necessary to fit the project’s actual service contracts and test model.

**Result**

The service now supports both scheduled and manual archival for expired audit events.

### 5. Earlier historical AI-assisted work (reconstructed from git history)

**Prompt given to AI**

> Generate the initial repository structure and setup for a Spring Boot audit log service project.

**AI output**

- Initial project skeleton and standard Java/Spring Boot structure
- Base conventions for source and test organization

**Accepted**

- Initial repository layout
- Standard Maven/Spring Boot project scaffolding

**Modified / discarded**

- Minor project conventions were adjusted to match the actual repository structure and requirements.

**Reason**

The project needed a clean starting baseline before implementing the domain-specific audit logic.

---

**Prompt given to AI**

> Create the initial architecture design for a tamper-evident audit log service using GitHub Copilot.

**AI output**

- Initial service architecture definition
- Layered design for controller, service, repository, and persistence
- Core concept for tamper-evident hash chaining

**Accepted**

- High-level architectural direction
- Layer separation and append-only event model

**Modified / discarded**

- The prototype was refined to fit the actual service design and local H2 development pattern.

**Reason**

The initial architecture established the foundation for later implementation, but it needed to be adapted to the actual project workflow and local development needs.

---

**Prompt given to AI**

> Document the decision to use H2 for local development while keeping the project architecture compatible with PostgreSQL for production.

**AI output**

- Local development rationale for H2
- Notes explaining the production migration path

**Accepted**

- H2 as the local dev database
- DB-agnostic architecture concept

**Modified / discarded**

- No major rejection; the decision was documentation-level rather than implementation-level.

**Reason**

Rapid local iteration was a practical necessity, while retaining a migration path to production-grade storage.

---

**Prompt given to AI**

> Add unit tests for the audit log service and controller, covering success and validation paths.

**AI output**

- Controller unit tests
- Service unit tests
- Coverage for main behavioral paths

**Accepted**

- Basic validation and service behavior checks
- Test structure aligned to the application design

**Modified / discarded**

- Test logic was refined to match changing DTO and service contracts during implementation.

**Reason**

Tests were needed to validate behavior while the service evolved across scenarios.

---

**Prompt given to AI**

> Add retention and redaction support to the audit log service and ensure the features work correctly with the existing tamper-evident model.

**AI output**

- Redaction logic
- Archive endpoint implementation
- Export support and filtering behavior

**Accepted**

- Redaction flow
- Archive-by-id behavior
- Export bundle logic for actor/resource queries

**Modified / discarded**

- The implementation was revised to avoid breaking chain integrity and to preserve export behavior for archived items.

**Reason**

The data must remain tamper-evident and consistent even when historical records are archived or redacted.

---

**Prompt given to AI**

> Implement retention-based archival and associated tests using scheduled and manual triggers.

**AI output**

- `RetentionPolicy` logic
- `@Scheduled` archival execution
- Manual retention trigger endpoint
- Test cases validating expired items are archived

**Accepted**

- Scheduled archival approach
- Manual retention trigger
- Test validation for retention-based archival

**Modified / discarded**

- The archival logic was refined to ensure it only marks events as archived and does not mutate the stored hash sequence.

**Reason**

Retention must support compliance and cleanup without breaking the integrity model or audit trust.

## Maintenance note

This usage log should be updated after each AI-assisted coding session. Each entry should capture:

- the prompt given to the AI,
- the AI output received,
- what was accepted,
- what was rejected,
- what was modified or discarded, and
- the rationale behind each decision.

This maintains a clear, reviewable record of AI-assisted development decisions and ensures accountability for the evolution of the project.

### 2026-08-13 — Validation, payload limits, and test additions

**Prompt given to AI**

> Add request payload size enforcement and DTO validation tests; link AI usage log in attestation.

**AI output / actions performed**

- Added `PayloadSizeFilter` to reject requests with `Content-Length` exceeding `audit.payload.max-bytes`.
- Added `@Size(max = 2048)` to `CreateAuditEventRequest.payload` to limit logical payload size.
- Added `ValidationExceptionHandler` (`@ControllerAdvice`) to return structured validation error responses.
- Added unit tests in `AuditEventControllerValidationTest` for oversized payload (expect 413) and DTO validation violation (expect 400).
- Updated `application.properties` and `application-prod.properties.example` with `audit.payload.max-bytes=8192` default.
- Linked `AI_USAGE_LOG.md` from `ATTESTATION.md` as a Markdown link.

**Accepted**

- The code changes above were applied; unit tests were run locally to validate behavior.

**Modified / discarded**

- Adjusted the DTO-validation test to bypass the `PayloadSizeFilter` so DTO validation is reached and returns 400.

**Reason**

Ensures incoming requests are bounded at the HTTP layer and logically validated at the DTO layer; keeps error semantics clear and testable.

**Result**

Files modified: `src/main/java/com/schwab/auditlog/web/PayloadSizeFilter.java`, `src/main/java/com/schwab/auditlog/web/ValidationExceptionHandler.java`, `src/main/java/com/schwab/auditlog/dto/CreateAuditEventRequest.java`, `src/test/java/com/schwab/auditlog/controller/AuditEventControllerValidationTest.java`, `src/main/resources/application.properties`, `src/main/resources/application-prod.properties.example`, and `ATTESTATION.md`.

Additions verified by running `mvn test` locally; test suite ran and the new tests executed (one earlier run reported 1 failure before adjustment; retested after fix).