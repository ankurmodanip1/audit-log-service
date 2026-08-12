package com.schwab.auditlog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.schwab.auditlog.dto.AuditEventResponse;
import com.schwab.auditlog.dto.AuditExportBundle;
import com.schwab.auditlog.dto.CreateAuditEventRequest;
import com.schwab.auditlog.dto.RedactRequest;
import com.schwab.auditlog.dto.VerifyResponse;
import com.schwab.auditlog.service.AuditEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditEventControllerTest {

    private AuditEventController controller;
    private AuditEventService service;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static class AuditEventServiceStub extends AuditEventService {
        AuditEventServiceStub() {
            super(null, null, null);
        }

        @Override
        public AuditEventResponse createEvent(CreateAuditEventRequest request) {
            return new AuditEventResponse(
                    1L,
                    request.getEventType(),
                    request.getActorId(),
                    request.getResourceType(),
                    request.getResourceId(),
                    "{\"ipAddress\":\"10.10.10.10\",\"status\":\"SUCCESS\"}",
                    request.getTimestamp(),
                    "computed-hash",
                    "GENESIS",
                    List.of()
            );
        }

        @Override
        public VerifyResponse verifyChain() {
            return new VerifyResponse(true, null, "Hash chain is valid");
        }

        @Override
        public AuditEventResponse redactEvent(Long id, RedactRequest request) {
            return new AuditEventResponse(
                    id,
                    "USER_LOGIN",
                    "user-101",
                    "ACCOUNT",
                    "ACC-001",
                    "{\"ipAddress\":\"[REDACTED]\",\"status\":\"SUCCESS\"}",
                    Instant.parse("2026-08-12T10:00:00Z"),
                    "computed-hash",
                    "GENESIS",
                    List.of("ipAddress")
            );
        }

        @Override
        public AuditEventResponse archiveEvent(Long id) {
            return new AuditEventResponse(
                    id,
                    "USER_LOGIN",
                    "user-101",
                    "ACCOUNT",
                    "ACC-001",
                    "{\"ipAddress\":\"10.10.10.10\",\"status\":\"SUCCESS\"}",
                    Instant.parse("2026-08-12T10:00:00Z"),
                    "computed-hash",
                    "GENESIS",
                    List.of()
            );
        }

        @Override
        public AuditExportBundle exportByActor(String actorId) {
            CreateAuditEventRequest request = new CreateAuditEventRequest();
            request.setEventType("USER_LOGIN");
            request.setActorId("user-101");
            request.setResourceType("ACCOUNT");
            request.setResourceId("ACC-001");
            request.setPayload(Map.of("ipAddress", "10.10.10.10", "status", "SUCCESS"));
            request.setTimestamp(Instant.parse("2026-08-12T10:00:00Z"));
            return new AuditExportBundle(actorId, null, "bundle-hash", List.of(createEvent(request)));
        }
    }

    @BeforeEach
    void setUp() {
        service = new AuditEventServiceStub();

        controller = new AuditEventController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void createEvent_shouldReturnCreatedEvent() throws Exception {
        CreateAuditEventRequest request = new CreateAuditEventRequest();
        request.setEventType("USER_LOGIN");
        request.setActorId("user-101");
        request.setResourceType("ACCOUNT");
        request.setResourceId("ACC-001");
        request.setPayload(Map.of("ipAddress", "10.10.10.10", "status", "SUCCESS"));
        request.setTimestamp(Instant.parse("2026-08-12T10:00:00Z"));

        mockMvc.perform(post("/audit/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("USER_LOGIN"))
                .andExpect(jsonPath("$.actorId").value("user-101"))
                .andExpect(jsonPath("$.currentHash").value("computed-hash"));
    }

    @Test
    void verifyChain_shouldReturnValidResponse() throws Exception {
        mockMvc.perform(get("/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.reason").value("Hash chain is valid"));
    }

    @Test
    void redactEvent_shouldReturnRedactedEvent() throws Exception {
        RedactRequest request = new RedactRequest();
        request.setFields(List.of("ipAddress"));

        mockMvc.perform(post("/audit/events/1/redact")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redactedFields[0]").value("ipAddress"))
                .andExpect(jsonPath("$.payload").value("{\"ipAddress\":\"[REDACTED]\",\"status\":\"SUCCESS\"}"));
    }

    @Test
    void archiveEvent_shouldReturnArchivedEvent() throws Exception {
        mockMvc.perform(post("/audit/events/1/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void exportByActor_shouldReturnBundle() throws Exception {
        mockMvc.perform(get("/audit/exports/actor/user-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorId").value("user-101"))
                .andExpect(jsonPath("$.bundleHash").value("bundle-hash"));
    }
}
