# audit-log-service

# Schwab Audit Log Service

## Overview

This project implements a tamper-evident audit log service using Java 17, Spring Boot, PostgreSQL, and SHA-256 hash chaining.

The service records audit events in append-only format and exposes APIs to create, search, and verify audit records.

## Architecture

Components:

- REST Controller
- Audit Event Service
- Hash Service
- PostgreSQL Database
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

Create PostgreSQL database:

```sql
CREATE DATABASE auditdb;
