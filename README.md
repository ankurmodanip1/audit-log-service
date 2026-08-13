# Schwab Audit Log Service

## Overview

This project implements a tamper-evident audit log service using Java 17, Spring Boot, H2, and SHA-256 hash chaining.

The service supports:
- creating audit events
- searching/filtering audit events
- verifying the full hash chain
- redacting sensitive payload fields
- archiving individual or expired events
- exporting non-archived events by actor or resource

The current implementation uses H2 for rapid local development and testing. The persistence layer is designed to remain adaptable for a production database if needed.

## Architecture

Key components:

- REST controller for audit endpoints
- Service layer for creation, verification, redaction, retention, and export logic
- JPA repository for persistence
- Hash service for payload and record integrity checks
- Retention policy for scheduled and manual archival

## Hash Chain Design

Each audit record stores:

- currentHash
- previousHash
- payloadHash

A record hash is derived from the event metadata and payload hash, using the previous hash in the chain. If a previous record is modified or tampered with, the verification process detects the mismatch.

## API Summary

### Create event

POST /audit/events

Example:
```bash
curl -X POST http://localhost:8080/audit/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "USER_LOGIN",
    "actorId": "user-101",
    "resourceType": "ACCOUNT",
    "resourceId": "ACC-001",
    "payload": {
      "ipAddress": "10.10.10.10",
      "status": "SUCCESS"
    },
    "timestamp": "2026-08-11T10:00:00Z"
  }'
```

### Search events

GET /audit/events?actorId=user-101&page=0&size=10

### Verify chain

GET /audit/verify

### Redact fields

POST /audit/events/{id}/redact

### Archive single event

POST /audit/events/{id}/archive

### Archive expired events

POST /audit/retention/archive

### Export by actor

GET /audit/exports/actor/{actorId}

### Export by resource

GET /audit/exports/resource/{resourceId}

## Run Locally

### Start application

```bash
mvn spring-boot:run
```

### H2 console

Open:
```text
http://localhost:8080/h2-console
```

### Production

Create `src/main/resources/application-prod.properties` (or set environment variables) and run with the `prod` profile:

```bash
# Example using environment variables
export SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/auditdb
export SPRING_DATASOURCE_USERNAME=your_db_user
export SPRING_DATASOURCE_PASSWORD=your_db_password

# Run with prod profile
mvn -Dspring-boot.run.profiles=prod spring-boot:run
```

See `src/main/resources/application-prod.properties.example` for a sample.

### Run tests

```bash
mvn test
```

## Notes

- Archived records are retained in storage but excluded from active export results.
- Hash verification is preserved even when records are archived or redacted.
- The current version is configured for local H2 development and is suitable for iterative validation and demos.
