# AI Usage Log

## Task

Initial Architecture Design

### Prompt

Act as a senior solution architect.

Design a Spring Boot based tamper-evident audit log service.

### AI Output

Suggested architecture with:
- Controller
- Service
- Repository
- H2 (development) / PostgreSQL (production)
- SHA-256 hash chain

### Accepted

### Accepted

- Package structure
- Controller design
- Entity model
- Repository layer
- SHA-256 hash chain approach

### Modified

Replaced PostgreSQL with H2 database during initial development and testing.

### Reason
H2 enables faster local iteration and reduces setup complexity during prototyping. The application architecture remains database-agnostic and can be migrated to PostgreSQL later with minimal changes.

### Rejected

None.

### Reason

Architecture aligned well with requirements.