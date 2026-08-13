package com.schwab.auditlog.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashServiceTest {

    @Test
    void payloadHashIsDeterministicAcrossFieldOrder() {
        HashService svc = new HashService();

        String a = "{\"b\":2,\"a\":1}";
        String b = "{\"a\":1,\"b\":2}";

        String ha = svc.calculatePayloadHash(a);
        String hb = svc.calculatePayloadHash(b);

        assertThat(ha).isEqualTo(hb);
    }
}
