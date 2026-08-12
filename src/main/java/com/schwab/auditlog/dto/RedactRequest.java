package com.schwab.auditlog.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class RedactRequest {

    @NotEmpty
    private List<String> fields;

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }
}
