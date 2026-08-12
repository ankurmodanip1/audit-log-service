package com.schwab.auditlog.dto;

import java.time.Instant;

public class AuditEventResponse {

    private Long id;
    private String eventType;
    private String actorId;
    private String resourceType;
    private String resourceId;
    private String payload;
    private Instant timestamp;
    private String currentHash;
    private String previousHash;

    public AuditEventResponse(Long id, String eventType, String actorId,
                              String resourceType, String resourceId,
                              String payload, Instant timestamp,
                              String currentHash, String previousHash) {
        this.id = id;
        this.eventType = eventType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.timestamp = timestamp;
        this.currentHash = currentHash;
        this.previousHash = previousHash;
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public String getPreviousHash() {
        return previousHash;
    }
}