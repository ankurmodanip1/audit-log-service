package com.schwab.auditlog.dto;

public class VerifyResponse {

    private boolean valid;
    private Long brokenRecordId;
    private String reason;

    public VerifyResponse(boolean valid, Long brokenRecordId, String reason) {
        this.valid = valid;
        this.brokenRecordId = brokenRecordId;
        this.reason = reason;
    }

    public boolean isValid() {
        return valid;
    }

    public Long getBrokenRecordId() {
        return brokenRecordId;
    }

    public String getReason() {
        return reason;
    }
}