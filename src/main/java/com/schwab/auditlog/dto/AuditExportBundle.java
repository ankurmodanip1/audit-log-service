package com.schwab.auditlog.dto;

import java.util.List;

public class AuditExportBundle {

    private String actorId;
    private String resourceId;
    private String bundleHash;
    private List<AuditEventResponse> records;

    public AuditExportBundle(String actorId, String resourceId, String bundleHash, List<AuditEventResponse> records) {
        this.actorId = actorId;
        this.resourceId = resourceId;
        this.bundleHash = bundleHash;
        this.records = records;
    }

    public String getActorId() {
        return actorId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getBundleHash() {
        return bundleHash;
    }

    public List<AuditEventResponse> getRecords() {
        return records;
    }
}
