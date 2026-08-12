package com.schwab.auditlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.auditlog.dto.AuditEventResponse;
import com.schwab.auditlog.dto.CreateAuditEventRequest;
import com.schwab.auditlog.dto.VerifyResponse;
import com.schwab.auditlog.entity.AuditEvent;
import com.schwab.auditlog.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {

    @Mock
    private AuditEventRepository repository;

    private HashService hashService;

    private ObjectMapper objectMapper;

    private AuditEventService service;

    private CreateAuditEventRequest request;
    private Instant timestamp;

    @BeforeEach
    void setUp() {
        timestamp = Instant.parse("2026-08-12T10:00:00Z");
        request = new CreateAuditEventRequest();
        request.setEventType("USER_LOGIN");
        request.setActorId("user-101");
        request.setResourceType("ACCOUNT");
        request.setResourceId("ACC-001");
        request.setPayload(Map.of("ipAddress", "10.10.10.10", "status", "SUCCESS"));
        request.setTimestamp(timestamp);
        hashService = new HashService();
        objectMapper = new ObjectMapper();
        service = new AuditEventService(repository, hashService, objectMapper);
    }

    @Test
    void createEvent_shouldPersistWithCalculatedHashAndPreviousHash() {
        when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditEventResponse response = service.createEvent(request);

        assertThat(response).isNotNull();
        assertThat(response.getEventType()).isEqualTo("USER_LOGIN");
        assertThat(response.getActorId()).isEqualTo("user-101");
        assertThat(response.getCurrentHash()).isNotBlank();
        assertThat(response.getPreviousHash()).isEqualTo("GENESIS");
    }

    @Test
    void searchEvents_shouldReturnMappedPage() {
        AuditEvent event = new AuditEvent();
        event.setEventType("USER_LOGIN");
        event.setActorId("user-101");
        event.setResourceType("ACCOUNT");
        event.setResourceId("ACC-001");
        event.setPayload("payload-json");
        event.setEventTimestamp(timestamp);
        event.setCurrentHash("computed-hash");
        event.setPreviousHash("GENESIS");

        Page<AuditEvent> page = new PageImpl<>(List.of(event), PageRequest.of(0, 10), 1);
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<AuditEvent>>any(), any(Pageable.class))).thenReturn(page);

        Page<AuditEventResponse> responsePage = service.searchEvents("user-101", "ACCOUNT", "ACC-001", "USER_LOGIN", timestamp, timestamp, PageRequest.of(0, 10));

        assertThat(responsePage.getTotalElements()).isEqualTo(1);
        AuditEventResponse response = responsePage.getContent().get(0);
        assertThat(response.getEventType()).isEqualTo("USER_LOGIN");
        assertThat(response.getActorId()).isEqualTo("user-101");
        assertThat(response.getCurrentHash()).isEqualTo("computed-hash");
    }

    @Test
    void verifyChain_shouldReturnValidWhenChainIsIntact() {
        AuditEvent firstEvent = new AuditEvent();
        firstEvent.setId(1L);
        firstEvent.setEventType("USER_LOGIN");
        firstEvent.setActorId("user-101");
        firstEvent.setResourceType("ACCOUNT");
        firstEvent.setResourceId("ACC-001");
        firstEvent.setPayload("payload-json");
        firstEvent.setEventTimestamp(timestamp);
        firstEvent.setPreviousHash("GENESIS");
        firstEvent.setCurrentHash(hashService.calculateHash(firstEvent));

        AuditEvent secondEvent = new AuditEvent();
        secondEvent.setId(2L);
        secondEvent.setEventType("USER_LOGOUT");
        secondEvent.setActorId("user-101");
        secondEvent.setResourceType("ACCOUNT");
        secondEvent.setResourceId("ACC-001");
        secondEvent.setPayload("payload-json-2");
        secondEvent.setEventTimestamp(timestamp.plusSeconds(60));
        secondEvent.setPreviousHash(firstEvent.getCurrentHash());
        secondEvent.setCurrentHash(hashService.calculateHash(secondEvent));

        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(firstEvent, secondEvent));

        VerifyResponse response = service.verifyChain();

        assertTrue(response.isValid());
        assertEquals("Hash chain is valid", response.getReason());
        assertThat(response.getBrokenRecordId()).isNull();
    }

    @Test
    void verifyChain_shouldReturnInvalidWhenPreviousHashMismatch() {
        AuditEvent event = new AuditEvent();
        event.setId(1L);
        event.setEventType("USER_LOGIN");
        event.setActorId("user-101");
        event.setResourceType("ACCOUNT");
        event.setResourceId("ACC-001");
        event.setPayload("payload-json");
        event.setEventTimestamp(timestamp);
        event.setPreviousHash("broken-hash");
        event.setCurrentHash("hash-1");

        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(event));

        VerifyResponse response = service.verifyChain();

        assertFalse(response.isValid());
        assertEquals(1L, response.getBrokenRecordId());
        assertEquals("Previous hash mismatch", response.getReason());
    }

    @Test
    void verifyChain_shouldReturnInvalidWhenCurrentHashMismatch() {
        AuditEvent event = new AuditEvent();
        event.setId(1L);
        event.setEventType("USER_LOGIN");
        event.setActorId("user-101");
        event.setResourceType("ACCOUNT");
        event.setResourceId("ACC-001");
        event.setPayload("payload-json");
        event.setEventTimestamp(timestamp);
        event.setPreviousHash("GENESIS");
        event.setCurrentHash("invalid-hash");

        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(event));

        VerifyResponse response = service.verifyChain();

        assertFalse(response.isValid());
        assertEquals(1L, response.getBrokenRecordId());
        assertEquals("Current hash mismatch. Record may have been modified.", response.getReason());
    }
}
