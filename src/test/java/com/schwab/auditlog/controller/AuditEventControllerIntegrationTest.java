package com.schwab.auditlog.controller;

import com.schwab.auditlog.entity.AuditEvent;
import com.schwab.auditlog.repository.AuditEventRepository;
import com.schwab.auditlog.service.HashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Duration;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuditEventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditEventRepository repository;

    @Autowired
    private HashService hashService;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void exportByActor_shouldReturnOnlyUnarchivedRecordsAndBundleHash() throws Exception {
        AuditEvent activeEvent = createEvent("user-101", "ACCOUNT", "ACC-001", false);
        AuditEvent archivedEvent = createEvent("user-101", "ACCOUNT", "ACC-002", true);

        AuditEvent savedActive = repository.save(activeEvent);
        repository.save(archivedEvent);

        String expectedBundleHash = hashService.sha256(savedActive.getId() + "|" + savedActive.getCurrentHash());

        mockMvc.perform(get("/audit/exports/actor/user-101")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorId").value("user-101"))
                .andExpect(jsonPath("$.records.length()").value(1))
                .andExpect(jsonPath("$.bundleHash").value(expectedBundleHash));
    }

    @Test
    void exportByResource_shouldReturnOnlyUnarchivedRecordsAndBundleHash() throws Exception {
        AuditEvent activeEvent = createEvent("user-102", "ACCOUNT", "ACC-003", false);
        AuditEvent archivedEvent = createEvent("user-102", "ACCOUNT", "ACC-003", true);

        AuditEvent savedActive = repository.save(activeEvent);
        repository.save(archivedEvent);

        String expectedBundleHash = hashService.sha256(savedActive.getId() + "|" + savedActive.getCurrentHash());

        mockMvc.perform(get("/audit/exports/resource/ACC-003")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value("ACC-003"))
                .andExpect(jsonPath("$.records.length()").value(1))
                .andExpect(jsonPath("$.bundleHash").value(expectedBundleHash));
    }

    @Test
    void redactEvent_shouldReturnRedactedPayloadAndFieldList() throws Exception {
        AuditEvent event = createEvent("user-103", "ACCOUNT", "ACC-004", false);
        AuditEvent savedEvent = repository.save(event);

        String requestBody = "{\"fields\":[\"ipAddress\"]}";

        mockMvc.perform(post("/audit/events/" + savedEvent.getId() + "/redact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId()))
                .andExpect(jsonPath("$.redactedFields[0]").value("ipAddress"))
                .andExpect(jsonPath("$.payload").value("{\"ipAddress\":\"[REDACTED]\",\"status\":\"SUCCESS\"}"));
    }

    @Test
    void archiveEvent_shouldMarkRecordArchivedAndExcludeFromExports() throws Exception {
        AuditEvent activeEvent = createEvent("user-104", "ACCOUNT", "ACC-005", false);
        AuditEvent savedEvent = repository.save(activeEvent);

        mockMvc.perform(post("/audit/events/" + savedEvent.getId() + "/archive")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId()))
                .andExpect(jsonPath("$.archived").value(true));

        mockMvc.perform(get("/audit/exports/actor/user-104")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records.length()").value(0));
    }

        @Test
        void retentionArchiveEndpoint_shouldArchiveOldEvents() throws Exception {
        AuditEvent oldEvent = createEvent("user-200", "ACCOUNT", "ACC-200", false);
        oldEvent.setEventTimestamp(Instant.now().minus(Duration.ofDays(31)));
        AuditEvent recentEvent = createEvent("user-200", "ACCOUNT", "ACC-200", false);
        recentEvent.setEventTimestamp(Instant.now());

        repository.save(oldEvent);
        repository.save(recentEvent);

        mockMvc.perform(post("/audit/retention/archive").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string("1"));

        mockMvc.perform(get("/audit/exports/actor/user-200").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.records.length()").value(1));
        }

    private AuditEvent createEvent(String actorId, String resourceType, String resourceId, boolean archived) {
        AuditEvent event = new AuditEvent();
        event.setEventType("USER_LOGIN");
        event.setActorId(actorId);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setPayload("{\"ipAddress\":\"10.10.10.10\",\"status\":\"SUCCESS\"}");
        event.setPayloadHash(hashService.calculatePayloadHash(event.getPayload()));
        event.setEventTimestamp(Instant.parse("2026-08-12T10:00:00Z"));
        event.setPreviousHash("GENESIS");
        event.setCreatedAt(Instant.now());
        event.setArchived(archived);
        event.setCurrentHash(hashService.calculateHash(event));
        return event;
    }

    @Test
    void verifyChain_shouldDetectTampering() throws Exception {
        AuditEvent first = createEvent("user-300", "ACCOUNT", "ACC-300", false);
        AuditEvent second = createEvent("user-300", "ACCOUNT", "ACC-300", false);

        AuditEvent savedFirst = repository.save(first);

        // link second event to first's current hash to form a valid chain
        second.setPreviousHash(savedFirst.getCurrentHash());
        second.setCurrentHash(hashService.calculateHash(second));
        AuditEvent savedSecond = repository.save(second);

        // Tamper with the second record's payload without updating its currentHash
        savedSecond.setPayload("{\"ipAddress\":\"1.2.3.4\",\"status\":\"FAIL\"}");
        repository.save(savedSecond);

        mockMvc.perform(get("/audit/verify").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.brokenRecordId").value(savedSecond.getId().intValue()))
            .andExpect(jsonPath("$.reason").value("Payload hash mismatch. Record payload may have been modified."));
    }
}
