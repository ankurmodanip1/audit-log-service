package com.schwab.auditlog.service;

import com.schwab.auditlog.entity.AuditEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashServiceEdgeCasesTest {

    @Test
    void calculatePayloadHash_handlesNullAndInvalidJson() {
        HashService svc = new HashService();

        String nullHash = svc.calculatePayloadHash(null);
        String emptyHash = svc.calculatePayloadHash("");
        // both should be non-null hex SHA-256 strings
        assertThat(nullHash).isNotNull();
        assertThat(emptyHash).isNotNull();
        assertThat(nullHash).matches("[0-9a-f]{64}");
        assertThat(emptyHash).matches("[0-9a-f]{64}");

        // invalid JSON should fallback to hashing raw string
        String raw = "not-a-json:{]";
        String rawHash = svc.calculatePayloadHash(raw);
        assertThat(rawHash).isNotNull();
        assertThat(rawHash).isEqualTo(svc.sha256(raw));
    }

    @Test
    void calculatePayloadHash_sortsArraysAndObjectsDeterministically() {
        HashService svc = new HashService();

        String withArray1 = "{\"arr\":[{\"b\":2,\"a\":1},{\"c\":3}],\"z\":1}";
        String withArray2 = "{\"z\":1,\"arr\":[{\"a\":1,\"b\":2},{\"c\":3}]}";

        String h1 = svc.calculatePayloadHash(withArray1);
        String h2 = svc.calculatePayloadHash(withArray2);

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void calculateHash_handlesNullFields() {
        HashService svc = new HashService();
        AuditEvent e = new AuditEvent();
        // leave all fields null
        e.setEventTimestamp(null);

        String h = svc.calculateHash(e);
        assertThat(h).isNotNull();

        // setting some fields changes hash
        e.setEventType("TYPE");
        String h2 = svc.calculateHash(e);
        assertThat(h2).isNotEqualTo(h);
    }
}
