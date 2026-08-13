package com.schwab.auditlog.service;

import com.schwab.auditlog.dto.CreateAuditEventRequest;
import com.schwab.auditlog.entity.AuditEvent;
import com.schwab.auditlog.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuditEventServiceTests {

    AuditEventRepository repo;
    HashService hashService;
    RetentionPolicy retentionPolicy;
    AuditEventService svc;

    @BeforeEach
    void setup() {
        repo = mock(AuditEventRepository.class);
        hashService = new HashService();
        retentionPolicy = new RetentionPolicy(java.time.Duration.ofDays(30));
        svc = new AuditEventService(repo, hashService, new com.fasterxml.jackson.databind.ObjectMapper(), retentionPolicy);
    }

    @Test
    void verifyChain_detectsPreviousHashMismatch() {
        AuditEvent a = new AuditEvent();
        a.setId(1L);
        a.setPreviousHash("NOT_GENESIS");
        a.setPayload("{}");
        a.setPayloadHash(hashService.calculatePayloadHash("{}"));
        a.setEventTimestamp(Instant.now());
        a.setCurrentHash(hashService.calculateHash(a));

        when(repo.findAllByOrderByIdAsc()).thenReturn(List.of(a));

        var resp = svc.verifyChain();
        assertThat(resp.isValid()).isFalse();
        assertThat(resp.getReason()).contains("Previous hash mismatch");
    }

    @Test
    void verifyChain_detectsPayloadTamper() {
        AuditEvent a = new AuditEvent();
        a.setId(2L);
        a.setPreviousHash("GENESIS");
        a.setPayload("{\"k\":1}");
        // set a wrong payloadHash to simulate tamper
        a.setPayloadHash("badpayloadhash");
        a.setEventTimestamp(Instant.now());
        a.setCurrentHash(hashService.calculateHash(a));

        when(repo.findAllByOrderByIdAsc()).thenReturn(List.of(a));

        var resp = svc.verifyChain();
        assertThat(resp.isValid()).isFalse();
        assertThat(resp.getReason()).contains("Payload hash mismatch");
    }

    @Test
    void archiveExpiredEvents_archivesFound() {
        AuditEvent old = new AuditEvent();
        old.setId(10L);
        old.setArchived(false);
        old.setEventTimestamp(Instant.now().minusSeconds(3600 * 24 * 365));

        when(repo.findAllByArchivedFalseAndEventTimestampBeforeOrderByIdAsc(any())).thenReturn(List.of(old));
        when(repo.saveAll(any())).thenReturn(List.of(old));

        int count = svc.archiveExpiredEvents();
        assertThat(count).isEqualTo(1);
        verify(repo).saveAll(any());
    }

    @Test
    void redactEvent_notFound_throws() {
        when(repo.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.redactEvent(999L, new com.schwab.auditlog.dto.RedactRequest()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createEvent_savesAndCalculatesHash() {
        CreateAuditEventRequest req = new CreateAuditEventRequest();
        req.setEventType("TYPE");
        req.setActorId("u1");
        req.setResourceType("RT");
        req.setResourceId("R1");
        req.setPayload(java.util.Map.of("k","v"));
        req.setTimestamp(Instant.parse("2026-08-01T00:00:00Z"));

        // repository finders return empty so previous=GENESIS
        when(repo.findLastEventForUpdate()).thenReturn(Optional.empty());
        when(repo.findTopByOrderByIdDesc()).thenReturn(Optional.empty());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        when(repo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        var resp = svc.createEvent(req);
        assertThat(resp.getEventType()).isEqualTo("TYPE");
        AuditEvent saved = captor.getValue();
        assertThat(saved.getPreviousHash()).isEqualTo("GENESIS");
        assertThat(saved.getCurrentHash()).isNotNull();
    }
}
