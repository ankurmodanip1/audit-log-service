package com.schwab.auditlog.controller;

import com.schwab.auditlog.dto.AuditEventResponse;
import com.schwab.auditlog.dto.CreateAuditEventRequest;
import com.schwab.auditlog.dto.VerifyResponse;
import com.schwab.auditlog.service.AuditEventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/audit")
public class AuditEventController {

    private final AuditEventService service;

    public AuditEventController(AuditEventService service) {
        this.service = service;
    }

    @PostMapping("/events")
    public AuditEventResponse createEvent(@Valid @RequestBody CreateAuditEventRequest request) {
        return service.createEvent(request);
    }

    @GetMapping("/events")
    public Page<AuditEventResponse> searchEvents(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            Pageable pageable
    ) {
        return service.searchEvents(actorId, resourceType, resourceId, eventType, from, to, pageable);
    }

    @GetMapping("/verify")
    public VerifyResponse verifyChain() {
        return service.verifyChain();
    }
}