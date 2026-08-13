package com.schwab.auditlog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.auditlog.dto.AuditEventResponse;
import com.schwab.auditlog.dto.AuditExportBundle;
import com.schwab.auditlog.dto.CreateAuditEventRequest;
import com.schwab.auditlog.dto.RedactRequest;
import com.schwab.auditlog.dto.VerifyResponse;
import com.schwab.auditlog.entity.AuditEvent;
import com.schwab.auditlog.repository.AuditEventRepository;
import com.schwab.auditlog.specification.AuditEventSpecification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuditEventService {

    private static final String GENESIS_HASH = "GENESIS";

    private final AuditEventRepository repository;
    private final HashService hashService;
    private final ObjectMapper objectMapper;
    private final RetentionPolicy retentionPolicy;

    public AuditEventService(AuditEventRepository repository,
                             HashService hashService,
                             ObjectMapper objectMapper,
                             RetentionPolicy retentionPolicy) {
        this.repository = repository;
        this.hashService = hashService;
        this.objectMapper = objectMapper;
        this.retentionPolicy = retentionPolicy;
    }

    @Transactional
    public AuditEventResponse createEvent(CreateAuditEventRequest request) {
        try {
            String payloadJson = objectMapper.writeValueAsString(request.getPayload());
            String payloadHash = hashService.calculatePayloadHash(payloadJson);

                    // Try to acquire a DB lock on the latest row to serialize chain append operations.
                    // If the custom locked finder isn't available (e.g. in simple mocks), fall back
                    // to the previous non-locking finder to keep tests stable.
                    String previousHash = repository.findLastEventForUpdate()
                        .map(AuditEvent::getCurrentHash)
                        .or(() -> repository.findTopByOrderByIdDesc().map(AuditEvent::getCurrentHash))
                        .orElse(GENESIS_HASH);

            AuditEvent event = new AuditEvent();
            event.setEventType(request.getEventType());
            event.setActorId(request.getActorId());
            event.setResourceType(request.getResourceType());
            event.setResourceId(request.getResourceId());
            event.setPayload(payloadJson);
            event.setPayloadHash(payloadHash);
            event.setRedactedFields(null);
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

            // Verify payload integrity: the stored payloadHash must match the actual
            // payload content. This detects tampering where payload was changed but
            // payloadHash was not updated.
            String recalculatedPayloadHash = hashService.calculatePayloadHash(event.getPayload());
            if (!recalculatedPayloadHash.equals(event.getPayloadHash())) {
                return new VerifyResponse(
                        false,
                        event.getId(),
                        "Payload hash mismatch. Record payload may have been modified."
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

    @Transactional
    public AuditEventResponse redactEvent(Long id, RedactRequest request) {
        try {
            AuditEvent event = repository.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Audit event not found"));

            Map<String, Object> payloadMap = objectMapper.readValue(event.getPayload(), new TypeReference<>() {
            });
            for (String field : request.getFields()) {
                if (payloadMap.containsKey(field)) {
                    payloadMap.put(field, "[REDACTED]");
                }
            }

            event.setPayload(objectMapper.writeValueAsString(payloadMap));
            event.setRedactedFields(String.join(",", request.getFields()));
            AuditEvent saved = repository.save(event);
            return mapToResponse(saved);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to redact audit event", exception);
        }
    }

    @Transactional
    public AuditEventResponse archiveEvent(Long id) {
        AuditEvent event = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Audit event not found"));

        event.setArchived(true);
        AuditEvent saved = repository.save(event);
        return mapToResponse(saved);
    }

    @Scheduled(cron = "${audit.retention.cron:0 0 0 * * ?}")
    @Transactional
    public int archiveExpiredEvents() {
        Instant cutoff = retentionPolicy.calculateCutoff(Instant.now());
        List<AuditEvent> eventsToArchive = repository
                .findAllByArchivedFalseAndEventTimestampBeforeOrderByIdAsc(cutoff);

        if (eventsToArchive.isEmpty()) {
            return 0;
        }

        eventsToArchive.forEach(event -> event.setArchived(true));
        repository.saveAll(eventsToArchive);
        return eventsToArchive.size();
    }

    public AuditExportBundle exportByActor(String actorId) {
        List<AuditEventResponse> records = repository
                .findAllByActorIdAndArchivedFalseOrderByIdAsc(actorId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        String bundleHash = calculateBundleHash(records);
        return new AuditExportBundle(actorId, null, bundleHash, records);
    }

    public AuditExportBundle exportByResource(String resourceId) {
        List<AuditEventResponse> records = repository
                .findAllByResourceIdAndArchivedFalseOrderByIdAsc(resourceId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        String bundleHash = calculateBundleHash(records);
        return new AuditExportBundle(null, resourceId, bundleHash, records);
    }

    private String calculateBundleHash(List<AuditEventResponse> records) {
        String canonical = records.stream()
                .sorted(Comparator.comparing(AuditEventResponse::getId))
                .map(record -> record.getId() + "|" + record.getCurrentHash())
                .collect(Collectors.joining("|"));

        return hashService.sha256(canonical);
    }

    private AuditEventResponse mapToResponse(AuditEvent event) {
        List<String> redactedFields = event.getRedactedFields() == null || event.getRedactedFields().isBlank()
                ? List.of()
                : List.of(event.getRedactedFields().split(","));

        return new AuditEventResponse(
                event.getId(),
                event.getEventType(),
                event.getActorId(),
                event.getResourceType(),
                event.getResourceId(),
                event.getPayload(),
                event.getEventTimestamp(),
                event.getCurrentHash(),
                event.getPreviousHash(),
                redactedFields,
                event.isArchived()
        );
    }
}
