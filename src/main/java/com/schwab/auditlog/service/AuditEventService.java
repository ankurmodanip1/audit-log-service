package com.schwab.auditlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.auditlog.dto.AuditEventResponse;
import com.schwab.auditlog.dto.CreateAuditEventRequest;
import com.schwab.auditlog.dto.VerifyResponse;
import com.schwab.auditlog.entity.AuditEvent;
import com.schwab.auditlog.repository.AuditEventRepository;
import com.schwab.auditlog.specification.AuditEventSpecification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuditEventService {

    private static final String GENESIS_HASH = "GENESIS";

    private final AuditEventRepository repository;
    private final HashService hashService;
    private final ObjectMapper objectMapper;

    public AuditEventService(AuditEventRepository repository,
                             HashService hashService,
                             ObjectMapper objectMapper) {
        this.repository = repository;
        this.hashService = hashService;
        this.objectMapper = objectMapper;
    }

    public AuditEventResponse createEvent(CreateAuditEventRequest request) {
        try {
            String payloadJson = objectMapper.writeValueAsString(request.getPayload());

            String previousHash = repository.findTopByOrderByIdDesc()
                    .map(AuditEvent::getCurrentHash)
                    .orElse(GENESIS_HASH);

            AuditEvent event = new AuditEvent();
            event.setEventType(request.getEventType());
            event.setActorId(request.getActorId());
            event.setResourceType(request.getResourceType());
            event.setResourceId(request.getResourceId());
            event.setPayload(payloadJson);
            event.setEventTimestamp(
                    request.getTimestamp() != null ? request.getTimestamp() : Instant.now()
            );
            event.setPreviousHash(previousHash);
            event.setCreatedAt(Instant.now());

            String hash = hashService.calculateHash(event);
            event.setCurrentHash(hash);

            AuditEvent savedEvent = repository.save(event);
            return mapToResponse(savedEvent);

        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create audit event", exception);
        }
    }

    public Page<AuditEventResponse> searchEvents(String actorId,
                                                 String resourceType,
                                                 String resourceId,
                                                 String eventType,
                                                 Instant from,
                                                 Instant to,
                                                 Pageable pageable) {
        return repository.findAll(
                AuditEventSpecification.filter(actorId, resourceType, resourceId, eventType, from, to),
                pageable
        ).map(this::mapToResponse);
    }

    public VerifyResponse verifyChain() {
        List<AuditEvent> events = repository.findAllByOrderByIdAsc();

        String expectedPreviousHash = GENESIS_HASH;

        for (AuditEvent event : events) {
            if (!expectedPreviousHash.equals(event.getPreviousHash())) {
                return new VerifyResponse(
                        false,
                        event.getId(),
                        "Previous hash mismatch"
                );
            }

            String recalculatedHash = hashService.calculateHash(event);

            if (!recalculatedHash.equals(event.getCurrentHash())) {
                return new VerifyResponse(
                        false,
                        event.getId(),
                        "Current hash mismatch. Record may have been modified."
                );
            }

            expectedPreviousHash = event.getCurrentHash();
        }

        return new VerifyResponse(true, null, "Hash chain is valid");
    }

    private AuditEventResponse mapToResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getEventType(),
                event.getActorId(),
                event.getResourceType(),
                event.getResourceId(),
                event.getPayload(),
                event.getEventTimestamp(),
                event.getCurrentHash(),
                event.getPreviousHash()
        );
    }
}