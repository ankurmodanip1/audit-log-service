# audit-log-service

# Schwab Audit Log Service

## Overview

This project implements a tamper-evident audit log service using Java 17, Spring Boot, H2 Database, and SHA-256 hash chaining.

The current implementation uses H2 for rapid local development and testing. The persistence layer is designed to support migration to PostgreSQL with minimal configuration changes.

The service records audit events in append-only format and exposes APIs to create, search, and verify audit records.

## Architecture

Components:

- REST Controller
- Audit Event Service
- Hash Service
- H2 Database (development) / PostgreSQL (production)
- Spring Data JPA Repository

## Hash Chain Design

Each audit record stores:

- currentHash
- previousHash

The current hash is calculated using:

eventType, actorId, resourceType, resourceId, payload, timestamp, previousHash

If any old record is modified, hash verification fails.

## APIs

### Create Event

POST /audit/events

### Search Events

GET /audit/events?actorId=user-101&page=0&size=10

### Verify Chain

GET /audit/verify

## Run Locally

### Start Application

```bash
mvn spring-boot:run

# Open the H2 console at: http://localhost:8080/h2-console
```
